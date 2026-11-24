package vn.cgnn.admin.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
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
            return writeWithNdef(ndef, ndefMessage, payloadSize, expectedUrl, uid, logs, onStageChange)
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return writeWithNdefFormatable(tag, formatable, ndefMessage, expectedUrl, uid, logs, onStageChange)
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

            logs.add("Đang ghi NDEF ($payloadSize bytes)...")
            ndef.writeNdefMessage(message)
            logs.add("Ghi xong, bắt đầu đọc kiểm tra...")

            onStageChange("verifying")
            // Give EEPROM a brief moment to settle before live RF readback
            runCatching { Thread.sleep(60L) }
            val readBack = ndef.ndefMessage
            val readUrls = extractNormalizedUrls(readBack)
            logs.add("Đọc kiểm tra trực tiếp: ${readUrls.joinToString()}")

            if (readUrls.contains(expectedUrl) && readUrls.all { it == expectedUrl }) {
                return NfcWriteResult.Success(uid = uid, verifiedUrl = expectedUrl, logs = logs)
            }

            logs.add("Mã đọc lại không khớp kỳ vọng")
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
            logs.add("Format + ghi thành công, kiểm tra lại...")

            onStageChange("verifying")
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                try {
                    ndef.connect()
                    val readUrls = extractNormalizedUrls(ndef.ndefMessage)
                    logs.add("Đọc kiểm tra sau format: ${readUrls.joinToString()}")
                    if (readUrls.contains(expectedUrl) && readUrls.all { it == expectedUrl }) {
                        return NfcWriteResult.Success(uid = uid, verifiedUrl = expectedUrl, logs = logs)
                    }
                } finally {
                    runCatching { ndef.close() }
                }
            }
            // Some Android chips don't expose Ndef immediately on the same Tag handle after format;
            // format() succeeding without throwing means the chip acknowledged the write.
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
}
