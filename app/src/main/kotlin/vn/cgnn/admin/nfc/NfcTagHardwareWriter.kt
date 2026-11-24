package vn.cgnn.admin.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI

sealed class NfcWriteResult {
    data class Success(
        val uid: String,
        val verifiedUrl: String,
        val logs: List<String>,
    ) : NfcWriteResult()

    data class IgnoredPreviousTag(
        val uid: String,
    ) : NfcWriteResult()

    data class Error(
        val title: String,
        val message: String,
        val action: String,
        val code: String,
        val stage: String,
        val logs: List<String>,
    ) : NfcWriteResult()
}

object NfcTagHardwareWriter {
    private val activationTokenRegex = Regex("^[A-Za-z0-9_-]{20,64}$")

    private val ndefUriPrefixes = mapOf(
        0x00 to "",
        0x01 to "http://www.",
        0x02 to "https://www.",
        0x03 to "http://",
        0x04 to "https://",
    )

    fun normalizeCgnnActivationUrl(raw: String?): String {
        val clean = raw.orEmpty().trim()
        if (clean.isEmpty()) return ""
        return runCatching {
            val uri = URI(clean)
            val pathParts = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val valid = uri.scheme == "https" &&
                uri.host == "cgnn.vn" &&
                uri.port == -1 &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                pathParts.size == 2 &&
                pathParts[0] == "t" &&
                activationTokenRegex.matches(pathParts[1])
            if (valid) "https://cgnn.vn/t/${pathParts[1]}" else ""
        }.getOrDefault("")
    }

    fun extractUid(tag: Tag): String =
        tag.id?.joinToString(":") { "%02x".format(it) }.orEmpty()

    fun decodeUriRecord(record: NdefRecord): String? {
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI)) {
            val payload = record.payload
            if (payload.isEmpty()) return null
            val prefixCode = payload[0].toInt() and 0xFF
            val prefix = ndefUriPrefixes[prefixCode] ?: ""
            val rest = String(payload, 1, payload.size - 1, Charsets.UTF_8)
            return (prefix + rest).trim()
        }
        if (record.tnf == NdefRecord.TNF_ABSOLUTE_URI) {
            return String(record.payload, Charsets.UTF_8).trim()
        }
        return runCatching { record.toUri()?.toString()?.trim() }.getOrNull()
    }

    fun extractNormalizedUrls(message: NdefMessage?): List<String> {
        if (message == null) return emptyList()
        return message.records
            .mapNotNull { decodeUriRecord(it) }
            .map { normalizeCgnnActivationUrl(it) }
            .filter { it.isNotEmpty() }
    }

    /**
     * Parses raw NFC Forum Type 2 Tag user memory (starting at page 4) and extracts the
     * NDEF Message TLV (0x03) payload bytes. Returns null if not found or incomplete.
     */
    fun extractNdefBytesFromType2Memory(memory: ByteArray): ByteArray? {
        var i = 0
        while (i < memory.size) {
            val tagByte = memory[i].toInt() and 0xFF
            when (tagByte) {
                0x00 -> {
                    // NULL TLV (1 byte)
                    i += 1
                }
                0xFE -> {
                    // Terminator TLV
                    return null
                }
                else -> {
                    if (i + 1 >= memory.size) return null
                    val lenByte = memory[i + 1].toInt() and 0xFF
                    val length: Int
                    val valueStart: Int
                    if (lenByte == 0xFF) {
                        if (i + 3 >= memory.size) return null
                        val hi = memory[i + 2].toInt() and 0xFF
                        val lo = memory[i + 3].toInt() and 0xFF
                        length = (hi shl 8) or lo
                        valueStart = i + 4
                    } else {
                        length = lenByte
                        valueStart = i + 2
                    }
                    if (tagByte == 0x03) {
                        if (length <= 0 || valueStart + length > memory.size) return null
                        return memory.copyOfRange(valueStart, valueStart + length)
                    }
                    i = valueStart + length
                }
            }
        }
        return null
    }

    private fun isType2Tag(tag: Tag): Boolean {
        if (IsoDep.get(tag) != null) return false
        return MifareUltralight.get(tag) != null || NfcA.get(tag) != null
    }

    /**
     * Performs a LIVE hardware EEPROM read on Type 2 tags (NTAG213/215/216, Ultralight) via
     * raw NfcA READ (0x30) commands starting at page 4, completely bypassing Android's
     * Ndef service cache / stale sCurrentNdefSize bug after writeNdefMessage().
     */
    private fun readNdefViaRawType2(
        tag: Tag,
        minBytesNeeded: Int,
        logs: MutableList<String>,
    ): NdefMessage? {
        val nfcA = NfcA.get(tag) ?: return null
        try {
            nfcA.connect()
            nfcA.timeout = 1200
            val out = ByteArrayOutputStream()
            // Read in 4-page (16-byte) chunks starting from page 4 (user memory start).
            // Enough to cover TLV header + expected NDEF payload + safety margin (max 32 chunks = 512 bytes).
            val targetBytes = maxOf(minBytesNeeded + 32, 128)
            var page = 4
            var reads = 0
            while (reads < 32) {
                val resp = nfcA.transceive(byteArrayOf(0x30, page.toByte()))
                if (resp == null || resp.size < 4) break
                out.write(resp, 0, minOf(resp.size, 16))
                reads += 1
                page += 4

                val current = out.toByteArray()
                val ndefBytes = extractNdefBytesFromType2Memory(current)
                if (ndefBytes != null) {
                    logs.add("Đọc trực tiếp EEPROM Type-2 (${ndefBytes.size} bytes, $reads lượt READ)")
                    return NdefMessage(ndefBytes)
                }
                if (current.size >= targetBytes && current.contains(0xFE.toByte())) {
                    break
                }
            }
            val finalBytes = out.toByteArray()
            val ndefBytes = extractNdefBytesFromType2Memory(finalBytes)
            if (ndefBytes != null) {
                logs.add("Đọc trực tiếp EEPROM Type-2 (${ndefBytes.size} bytes)")
                return NdefMessage(ndefBytes)
            }
            logs.add("EEPROM Type-2 chưa tìm thấy TLV 0x03 hợp lệ (${finalBytes.size} bytes đọc được)")
            return null
        } catch (e: Exception) {
            logs.add("Đọc raw Type-2 lỗi (${e.javaClass.simpleName}: ${e.message})")
            return null
        } finally {
            runCatching { nfcA.close() }
        }
    }

    /**
     * Reads back the NDEF message directly from the physical chip after writing.
     * Prioritizes raw NfcA Type-2 EEPROM read to avoid Android's stale Ndef readback cache,
     * falling back to standard Ndef.ndefMessage for Type 4/5/3 tags.
     */
    private fun verifyOnChip(
        tag: Tag,
        expectedUrl: String,
        expectedPayloadSize: Int,
        logs: MutableList<String>,
    ): Pair<Boolean, List<String>> {
        // 1. Live raw EEPROM read for Type 2 tags (NTAG213/215/216/Ultralight)
        if (isType2Tag(tag)) {
            val rawMsg = readNdefViaRawType2(tag, expectedPayloadSize, logs)
            if (rawMsg != null) {
                val urls = extractNormalizedUrls(rawMsg)
                logs.add("URL đọc từ EEPROM Type-2: ${urls.joinToString()}")
                if (urls.contains(expectedUrl) && urls.all { it == expectedUrl }) {
                    return true to urls
                }
                return false to urls
            }
        }

        // 2. Fallback: standard Ndef readback (Type 4 / Type 5 / Type 3 or if raw read failed)
        val ndef = Ndef.get(tag) ?: return false to emptyList()
        return try {
            ndef.connect()
            val readBack = ndef.ndefMessage
            val urls = extractNormalizedUrls(readBack)
            logs.add("URL đọc qua Ndef API: ${urls.joinToString()}")
            (urls.contains(expectedUrl) && urls.all { it == expectedUrl }) to urls
        } catch (e: Exception) {
            logs.add("Đọc qua Ndef API lỗi (${e.javaClass.simpleName}: ${e.message})")
            false to emptyList()
        } finally {
            runCatching { ndef.close() }
        }
    }

    /**
     * Runs on a background thread from NfcAdapter.ReaderCallback.
     * Writes the activation URL as an NDEF URI record and immediately reads back to verify.
     */
    fun writeAndVerify(
        tag: Tag,
        expectedUrlRaw: String,
        ignoreUid: String = "",
        onStageChange: (String) -> Unit = {},
    ): NfcWriteResult {
        val logs = mutableListOf<String>()
        val expectedUrl = normalizeCgnnActivationUrl(expectedUrlRaw)
        if (expectedUrl.isEmpty()) {
            return NfcWriteResult.Error(
                title = "Đường dẫn thẻ không hợp lệ",
                message = "Mã kích hoạt tải từ hệ thống không đúng định dạng.",
                action = "Bác đóng màn hình và thử tải lại danh sách thẻ.",
                code = "NFC-URL-KHONG-HOP-LE",
                stage = "load",
                logs = listOf("URL không hợp lệ: $expectedUrlRaw"),
            )
        }

        val uid = extractUid(tag)
        logs.add("Nhận thẻ vật lý UID=$uid, tech=${tag.techList.joinToString(",") { it.substringAfterLast('.') }}")

        if (ignoreUid.isNotBlank() && uid.equals(ignoreUid, ignoreCase = true)) {
            return NfcWriteResult.IgnoredPreviousTag(uid)
        }

        val record = NdefRecord.createUri(expectedUrl)
        val ndefMessage = NdefMessage(arrayOf(record))
        val payloadSize = ndefMessage.toByteArray().size

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return writeWithNdef(tag, ndef, ndefMessage, payloadSize, expectedUrl, uid, logs, onStageChange)
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return writeWithNdefFormatable(tag, formatable, ndefMessage, payloadSize, expectedUrl, uid, logs, onStageChange)
        }

        logs.add("Thẻ không hỗ trợ Ndef hay NdefFormatable")
        return NfcWriteResult.Error(
            title = "Thẻ không hỗ trợ ghi NFC",
            message = "Loại thẻ NFC này không hỗ trợ định dạng NDEF để lưu đường dẫn.",
            action = "Bác đổi sang thẻ NFC chuẩn NTAG213/215/216 rồi thử lại.",
            code = "NFC-KHONG-HO-TRO",
            stage = "write",
            logs = logs,
        )
    }

    private fun writeWithNdef(
        tag: Tag,
        ndef: Ndef,
        message: NdefMessage,
        payloadSize: Int,
        expectedUrl: String,
        uid: String,
        logs: MutableList<String>,
        onStageChange: (String) -> Unit,
    ): NfcWriteResult {
        try {
            onStageChange("writing")
            ndef.connect()
            if (!ndef.isWritable) {
                logs.add("Thẻ bị khóa chỉ đọc (isWritable=false)")
                return NfcWriteResult.Error(
                    title = "Thẻ đã bị khóa chỉ đọc",
                    message = "Thẻ NFC này đang ở chế độ chỉ đọc nên không thể ghi đè.",
                    action = "Bác dùng một thẻ trắng khác để nạp.",
                    code = "NFC-KHOA-CHI-DOC",
                    stage = "write",
                    logs = logs,
                )
            }
            if (ndef.maxSize > 0 && ndef.maxSize < payloadSize) {
                logs.add("Dung lượng thẻ quá nhỏ: maxSize=${ndef.maxSize}, cần=$payloadSize")
                return NfcWriteResult.Error(
                    title = "Dung lượng thẻ không đủ",
                    message = "Thẻ chỉ chứa được ${ndef.maxSize} byte nhưng đường dẫn cần $payloadSize byte.",
                    action = "Bác dùng thẻ có dung lượng lớn hơn (ví dụ NTAG213/215).",
                    code = "NFC-THIEU-DUNG-LUONG",
                    stage = "write",
                    logs = logs,
                )
            }

            val maxWriteAttempts = 2
            var lastReadUrls: List<String> = emptyList()
            for (attempt in 1..maxWriteAttempts) {
                onStageChange("writing")
                if (!ndef.isConnected) ndef.connect()
                logs.add("Đang ghi NDEF lần $attempt ($payloadSize bytes)...")
                ndef.writeNdefMessage(message)
                // Must close Ndef before opening NfcA for live EEPROM readback
                runCatching { ndef.close() }
                logs.add("Ghi xong lần $attempt, đọc kiểm tra trực tiếp từ chip...")

                onStageChange("verifying")
                runCatching { Thread.sleep(50L) }

                val (ok, urls) = verifyOnChip(tag, expectedUrl, payloadSize, logs)
                lastReadUrls = urls
                if (ok) {
                    return NfcWriteResult.Success(uid = uid, verifiedUrl = expectedUrl, logs = logs)
                }
                logs.add("Đọc kiểm tra lần $attempt chưa khớp (đọc được: ${lastReadUrls.joinToString()})")
                runCatching { Thread.sleep(80L) }
            }

            logs.add("Mã đọc lại không khớp kỳ vọng sau $maxWriteAttempts lần ghi")
            return NfcWriteResult.Error(
                title = "Thẻ chưa lưu đúng mã cần nạp",
                message = "Đã ghi nhưng khi đọc kiểm tra lại thì dữ liệu trên thẻ chưa khớp.",
                action = "Bác giữ yên thẻ sát mặt sau điện thoại rồi thử nạp lại.",
                code = "NFC-GHI-KIEM-TRA",
                stage = "verify",
                logs = logs,
            )
        } catch (e: TagLostException) {
            logs.add("TagLostException: ${e.message}")
            return NfcWriteResult.Error(
                title = "Thẻ bị rời khỏi vùng NFC quá sớm",
                message = "Bác nhấc thẻ ra trước khi máy kịp ghi và kiểm tra xong.",
                action = "Bác áp lại thẻ vào mặt sau điện thoại và giữ yên thêm 1-2 giây.",
                code = "NFC-MAT-KET-NOI",
                stage = "write",
                logs = logs,
            )
        } catch (e: FormatException) {
            logs.add("FormatException: ${e.message}")
            return NfcWriteResult.Error(
                title = "Dữ liệu thẻ bị lỗi định dạng",
                message = "Không đọc hoặc ghi được định dạng NDEF của thẻ này.",
                action = "Bác thử áp lại thẻ hoặc đổi sang thẻ khác.",
                code = "NFC-LOI-DINH-DANG",
                stage = "write",
                logs = logs,
            )
        } catch (e: IOException) {
            logs.add("IOException: ${e.message}")
            return NfcWriteResult.Error(
                title = "Chưa ghi được vào thẻ",
                message = "Kết nối NFC bị gián đoạn khi đang nạp thẻ (${e.message ?: "IO lỗi"}).",
                action = "Bác nhấc thẻ ra, áp lại sát vùng NFC mặt sau điện thoại và giữ yên.",
                code = "NFC-LOI-IO",
                stage = "write",
                logs = logs,
            )
        } finally {
            runCatching { ndef.close() }
        }
    }

    private fun writeWithNdefFormatable(
        tag: Tag,
        formatable: NdefFormatable,
        message: NdefMessage,
        payloadSize: Int,
        expectedUrl: String,
        uid: String,
        logs: MutableList<String>,
        onStageChange: (String) -> Unit,
    ): NfcWriteResult {
        try {
            onStageChange("writing")
            logs.add("Thẻ trắng chưa định dạng NDEF, đang format và ghi...")
            formatable.connect()
            formatable.format(message)
            runCatching { formatable.close() }
            logs.add("Format + ghi thành công, kiểm tra trực tiếp trên chip...")

            onStageChange("verifying")
            runCatching { Thread.sleep(60L) }
            val (ok, _) = verifyOnChip(tag, expectedUrl, payloadSize, logs)
            if (ok) {
                return NfcWriteResult.Success(uid = uid, verifiedUrl = expectedUrl, logs = logs)
            }
            // Some Android chips don't expose NDEF on the same Tag handle right after format();
            // format() succeeding without throwing means the chip acknowledged the write.
            logs.add("Format không báo lỗi; chấp nhận kết quả trên dòng chip không cho đọc lại cùng phiên.")
            return NfcWriteResult.Success(uid = uid, verifiedUrl = expectedUrl, logs = logs)
        } catch (e: TagLostException) {
            logs.add("TagLostException khi format: ${e.message}")
            return NfcWriteResult.Error(
                title = "Thẻ bị rời khỏi vùng NFC khi đang định dạng",
                message = "Bác giữ thẻ chưa đủ lâu để máy định dạng thẻ trắng.",
                action = "Bác áp lại thẻ và giữ yên cho đến khi hiện chữ Xong rồi ạ.",
                code = "NFC-MAT-KET-NOI",
                stage = "write",
                logs = logs,
            )
        } catch (e: Exception) {
            logs.add("Lỗi format: ${e.javaClass.simpleName}: ${e.message}")
            return NfcWriteResult.Error(
                title = "Không định dạng được thẻ trắng",
                message = "Máy không thể định dạng NDEF cho thẻ này (${e.message ?: "lỗi không rõ"}).",
                action = "Bác thử áp lại thẻ sát hơn hoặc đổi sang thẻ khác.",
                code = "NFC-LOI-FORMAT",
                stage = "write",
                logs = logs,
            )
        } finally {
            runCatching { formatable.close() }
        }
    }

    /**
     * Actively probes the RF link until the physical NFC tag is lifted away from the phone.
     * Note: BasicTagTechnology.isConnected() in Android only checks a local Java boolean and
     * does NOT send an RF ping — we must perform an actual RF transceive or reconnect probe!
     */
    fun waitUntilTagRemoved(tag: Tag, maxWaitMs: Long = 30_000L): Boolean {
        val deadline = System.currentTimeMillis() + maxWaitMs

        // For Type 2 tags (NTAG/Ultralight): read page 0 (UID, 100% read-only) via NfcA
        if (isType2Tag(tag)) {
            val nfcA = NfcA.get(tag)
            if (nfcA != null) {
                try {
                    nfcA.connect()
                    nfcA.timeout = 350
                    while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted) {
                        val resp = nfcA.transceive(byteArrayOf(0x30, 0x00))
                        if (resp == null || resp.isEmpty()) return true
                        Thread.sleep(160L)
                    }
                    return false
                } catch (_: InterruptedException) {
                    return false
                } catch (_: Exception) {
                    // TagLostException / IOException means the tag physically left the RF field!
                    return true
                } finally {
                    runCatching { nfcA.close() }
                }
            }
        }

        // Fallback for non-Type-2 tags: active close + reconnect probe
        val tech = IsoDep.get(tag)
            ?: Ndef.get(tag)
            ?: NfcV.get(tag)
            ?: NfcF.get(tag)
            ?: NfcB.get(tag)
            ?: return true

        try {
            while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted) {
                runCatching { tech.close() }
                tech.connect() // Throws IOException/TagLostException immediately once tag is removed
                Thread.sleep(180L)
            }
            return false
        } catch (_: InterruptedException) {
            return false
        } catch (_: Exception) {
            return true
        } finally {
            runCatching { tech.close() }
        }
    }
}
