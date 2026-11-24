package vn.cgnn.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.cgnn.admin.data.model.NfcGuideCardData
import vn.cgnn.admin.data.model.NfcGuideContent
import vn.cgnn.admin.data.network.AdminAuthUtils
import vn.cgnn.admin.nfc.NfcTagHardwareWriter
import vn.cgnn.admin.util.NfcGuideHtmlBuilder
import vn.cgnn.admin.util.QrCodeSvgGenerator

class NfcAdminUnitTest {
    @Test
    fun normalizeActivationUrl_validAndInvalid() {
        val valid = "https://cgnn.vn/t/AbCdEfGhIjKlMnOpQrSt1234"
        assertEquals(valid, NfcTagHardwareWriter.normalizeCgnnActivationUrl("  $valid  "))
        assertEquals("", NfcTagHardwareWriter.normalizeCgnnActivationUrl("http://cgnn.vn/t/AbCdEfGhIjKlMnOpQrSt1234"))
        assertEquals("", NfcTagHardwareWriter.normalizeCgnnActivationUrl("https://evil.com/t/AbCdEfGhIjKlMnOpQrSt1234"))
        assertEquals("", NfcTagHardwareWriter.normalizeCgnnActivationUrl("https://cgnn.vn/t/short"))
        assertEquals("", NfcTagHardwareWriter.normalizeCgnnActivationUrl("https://cgnn.vn/t/AbCdEfGhIjKlMnOpQrSt1234?x=1"))
    }

    @Test
    fun qrCodeGenerator_producesValidSvg() {
        val url = "https://cgnn.vn/t/AbCdEfGhIjKlMnOpQrSt12345678"
        val matrix = QrCodeSvgGenerator.encode(url)
        assertTrue(matrix.size >= 25)
        // Top-left finder pattern corner must be dark
        assertTrue(matrix[0][0])
        assertTrue(matrix[6][6])
        assertFalse(matrix[1][1])
        val svg = QrCodeSvgGenerator.toSvg(url)
        assertTrue(svg.startsWith("<svg "))
        assertTrue(svg.contains("<path d="))
    }

    @Test
    fun staffRoleMatching_matchesAdminAndTechnician() {
        assertTrue(AdminAuthUtils.isMachineCareStaffRole("admin"))
        assertTrue(AdminAuthUtils.isMachineCareStaffRole("Super_Admin"))
        assertTrue(AdminAuthUtils.isMachineCareStaffRole("Kỹ thuật"))
        assertTrue(AdminAuthUtils.isMachineCareStaffRole("staff"))
        assertFalse(AdminAuthUtils.isMachineCareStaffRole("customer"))
        assertFalse(AdminAuthUtils.isMachineCareStaffRole(""))
    }

    @Test
    fun guideHtmlBuilder_buildsSlipAndDiff() {
        val diffEmpty = NfcGuideHtmlBuilder.diffContent(NfcGuideContent.DEFAULT)
        assertTrue(diffEmpty.isEmpty())

        val custom = NfcGuideContent.DEFAULT.copy(title = "THẺ CGNN")
        val diff = NfcGuideHtmlBuilder.diffContent(custom)
        assertEquals(1, diff.size)

        val html = NfcGuideHtmlBuilder.buildHtml(
            cards = listOf(
                NfcGuideCardData(
                    tagId = 1L,
                    name = "Bác Hai",
                    code = "1234ABCD",
                    link = "https://cgnn.vn/t/AbCdEfGhIjKlMnOpQrSt1234",
                ),
            ),
            content = custom,
        )
        assertTrue(html.contains("THẺ CGNN"))
        assertTrue(html.contains("Bác Hai"))
        assertTrue(html.contains("1234ABCD"))
        assertTrue(html.contains("<svg"))
    }

    @Test
    fun type2TlvParser_extractsNdefPayloadCleanly() {
        val fakeNdef = byteArrayOf(0xD1.toByte(), 0x01, 0x05, 0x55, 0x04, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte())
        // Leading NULL (0x00) + Lock Control TLV (0x01, len 3, 3 bytes) + NDEF TLV (0x03) + Terminator (0xFE)
        val mem = byteArrayOf(
            0x00,
            0x01, 0x03, 0xA0.toByte(), 0x0C, 0x34,
            0x03, fakeNdef.size.toByte(),
        ) + fakeNdef + byteArrayOf(0xFE.toByte(), 0x00, 0x00)

        val extracted = NfcTagHardwareWriter.extractNdefBytesFromType2Memory(mem)
        assertTrue(extracted != null && extracted.contentEquals(fakeNdef))

        // Empty tag memory returns null
        assertEquals(null, NfcTagHardwareWriter.extractNdefBytesFromType2Memory(byteArrayOf(0x03, 0x00, 0xFE.toByte())))

        // 3-byte extended length TLV (0x03, 0xFF, hi, lo)
        val bigPayload = ByteArray(260) { (it % 127).toByte() }
        val extMem = byteArrayOf(0x03, 0xFF.toByte(), 0x01, 0x04) + bigPayload + byteArrayOf(0xFE.toByte())
        val extractedBig = NfcTagHardwareWriter.extractNdefBytesFromType2Memory(extMem)
        assertTrue(extractedBig != null && extractedBig.contentEquals(bigPayload))
    }
}
