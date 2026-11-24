package vn.cgnn.admin.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.cgnn.admin.data.model.NfcGuideCardData
import vn.cgnn.admin.data.model.NfcGuideContent

object NfcGuideHtmlBuilder {
    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    fun diffContent(content: NfcGuideContent): JsonObject {
        val def = NfcGuideContent.DEFAULT
        return buildJsonObject {
            if (content.title.trim().ifEmpty { def.title } != def.title) put("title", content.title.trim())
            if (content.subtitle.trim().ifEmpty { def.subtitle } != def.subtitle) put("subtitle", content.subtitle.trim())
            if (content.forLabel.trim().ifEmpty { def.forLabel } != def.forLabel) put("for_label", content.forLabel.trim())
            val taglines = content.tagline.map { it.trim() }.filter { it.isNotEmpty() }.take(3).ifEmpty { def.tagline }
            if (taglines.joinToString("|") != def.tagline.joinToString("|")) {
                put("tagline", buildJsonArray { taglines.forEach { add(JsonPrimitive(it)) } })
            }
            if (content.step1Title.trim().ifEmpty { def.step1Title } != def.step1Title) put("step1_title", content.step1Title.trim())
            if (content.step1Text.trim().ifEmpty { def.step1Text } != def.step1Text) put("step1_text", content.step1Text.trim())
            if (content.step2Title.trim().ifEmpty { def.step2Title } != def.step2Title) put("step2_title", content.step2Title.trim())
            if (content.step2Text.trim().ifEmpty { def.step2Text } != def.step2Text) put("step2_text", content.step2Text.trim())
            if (content.step3Title.trim().ifEmpty { def.step3Title } != def.step3Title) put("step3_title", content.step3Title.trim())
            if (content.step3Text.trim().ifEmpty { def.step3Text } != def.step3Text) put("step3_text", content.step3Text.trim())
            if (content.phone.trim().ifEmpty { def.phone } != def.phone) put("phone", content.phone.trim())
            if (content.foot1.trim().ifEmpty { def.foot1 } != def.foot1) put("foot1", content.foot1.trim())
            if (content.foot2.trim().ifEmpty { def.foot2 } != def.foot2) put("foot2", content.foot2.trim())
            if (content.wish.trim().ifEmpty { def.wish } != def.wish) put("wish", content.wish.trim())
        }
    }

    private const val CSS = """
@page { size: 75mm 130mm; margin: 0; }
* { box-sizing: border-box; }
html, body { margin: 0; background: #fff; font-family: "Segoe UI", Arial, sans-serif; color: #111; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.page { width: 75mm; height: 129mm; display: flex; align-items: center; justify-content: center; overflow: hidden; break-after: page; }
.page:last-child { break-after: auto; }
.slip { flex: 0 0 auto; zoom: 0.46; width: 540px; background: #fff; border: 3px solid #1a1a1a; border-radius: 22px; padding: 14px 18px 12px; position: relative; display: flex; flex-direction: column; gap: 8px; overflow: hidden; break-inside: avoid; }
.head { display: flex; align-items: center; gap: 12px; }
.head svg { width: 92px; flex: 0 0 auto; }
.title { flex: 1; min-width: 0; line-height: 1; }
.title b { display: block; font-family: "Arial Black", "Segoe UI", sans-serif; font-weight: 900; font-size: 50px; letter-spacing: -1px; }
.title span { display: block; margin-top: 4px; font-weight: 900; font-size: 25px; }
.customer { text-align: center; font-size: 22px; font-weight: 800; border: 2.5px solid #1a1a1a; border-radius: 14px; padding: 6px 10px; background: #f3faf3; overflow-wrap: anywhere; }
.customer small { font-weight: 800; font-size: 16px; color: #444; margin-right: 6px; }
.tagline { display: flex; align-items: center; gap: 8px; font-style: italic; font-weight: 700; font-size: 17px; color: #111; }
.tagline::before, .tagline::after { content: ""; flex: 1; height: 3px; background: #1a1a1a; }
.tagline i { color: #7fb98a; font-style: normal; }
.step { position: relative; border: 3px solid #1a1a1a; border-radius: 16px; padding: 46px 14px 12px; }
.badge { position: absolute; left: -10px; top: -8px; display: flex; align-items: center; }
.badge .n { width: 54px; height: 54px; border-radius: 50%; background: #111; color: #fff; font-family: "Arial Black", sans-serif; font-size: 36px; display: grid; place-items: center; z-index: 1; }
.badge .t { margin-left: -14px; padding: 8px 18px 8px 24px; background: #1d1d1d; color: #fff; font-weight: 900; font-size: 22px; border-radius: 0 24px 24px 0; white-space: nowrap; }
.step p { margin: 0; font-size: 18px; line-height: 1.32; font-weight: 800; }
.row1 { display: flex; align-items: center; gap: 12px; }
.row1 .txt { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: flex-start; gap: 8px; }
.row1 p { font-size: 20px; }
.phone-ico svg { width: 58px; display: block; }
.qr { border: 3px solid #7fb98a; border-radius: 8px; padding: 3px; background: #fff; line-height: 0; }
.qr svg { width: 100%; height: auto; }
.row1 .qr { width: 270px; flex: 0 0 auto; }
.code-box { border: 3px solid #1a1a1a; border-radius: 12px; background: #fff7c2; font-family: "Consolas", "Courier New", monospace; font-weight: 900; line-height: 1; margin-top: 10px; text-align: center; font-size: 42px; letter-spacing: 5px; padding: 12px 4px; }
.s3row { display: flex; align-items: center; gap: 10px; }
.s3row p { flex: 1; }
.callbox { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.callbox .ico { width: 44px; height: 44px; }
.callbox .phone { background: #111; color: #fff; border-radius: 22px; padding: 6px 14px; font-weight: 800; font-size: 20px; white-space: nowrap; }
.foot { margin-top: 10px; border-top: 3px solid #1a1a1a; display: grid; grid-template-columns: 1fr 1fr 1.2fr; padding-top: 8px; font-size: 13px; font-weight: 700; line-height: 1.2; text-align: center; align-items: center; }
.foot > div { padding: 0 6px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.foot > div + div { border-left: 3px solid #1a1a1a; }
.foot svg { width: 34px; height: 40px; }
.wish { font-weight: 700; font-style: italic; font-size: 15px; line-height: 1.15; transform: rotate(-5deg); }
"""

    private const val TRACTOR = """<svg viewBox="0 0 120 100"><path d="M30 8h42l8 28h14l6 26H20z" fill="#111"/><path d="M38 16h12v20H36zM56 16h14l5 20H56z" fill="#fff"/><circle cx="30" cy="70" r="26" fill="#111"/><circle cx="30" cy="70" r="9" fill="#fff"/><circle cx="30" cy="70" r="4" fill="#111"/><circle cx="96" cy="78" r="17" fill="#111"/><circle cx="96" cy="78" r="6" fill="#fff"/><circle cx="96" cy="78" r="3" fill="#111"/><rect x="46" y="60" width="44" height="10" fill="#111"/></svg>"""
    private const val PHONE = """<svg viewBox="0 0 90 150"><rect x="6" y="4" width="78" height="142" rx="14" fill="#fff" stroke="#111" stroke-width="5"/><rect x="30" y="12" width="30" height="5" rx="2.5" fill="#111"/><path d="M22 40h14M22 40v14M68 40H54M68 40v14M22 112h14M22 112V98M68 112H54M68 112V98" fill="none" stroke="#7fb98a" stroke-width="5" stroke-linecap="round"/><rect x="34" y="58" width="22" height="22" fill="#111"/><rect x="38" y="62" width="14" height="14" fill="#fff"/><rect x="42" y="66" width="6" height="6" fill="#111"/></svg>"""
    private const val CALL = """<svg class="ico" viewBox="0 0 48 48"><path d="M14 6l8 2 2 9-5 3a24 24 0 0 0 9 9l3-5 9 2 2 8c-1 4-4 6-8 6C21 40 8 27 8 14c0-4 2-7 6-8z" fill="#7fb98a"/></svg>"""
    private const val SHIELD = """<svg viewBox="0 0 48 56"><path d="M24 3l19 7v16c0 13-8 22-19 27C13 48 5 39 5 26V10z" fill="#fff" stroke="#111" stroke-width="4"/><path d="M15 27l7 7 12-14" fill="none" stroke="#7fb98a" stroke-width="5"/></svg>"""

    fun buildHtml(cards: List<NfcGuideCardData>, content: NfcGuideContent): String {
        val c = content
        val taglines = c.tagline.ifEmpty { NfcGuideContent.DEFAULT_TAGLINE }
        val pages = cards.joinToString("") { card ->
            val customerBlock = if (card.name.isNotBlank()) {
                """<div class="customer"><small>${escapeHtml(c.forLabel)}</small><span>${escapeHtml(card.name)}</span></div>"""
            } else ""
            val qr = QrCodeSvgGenerator.toSvg(card.link)
            """
            <div class="page">
              <article class="slip">
                <div class="head">$TRACTOR<div class="title"><b>${escapeHtml(c.title)}</b><span>${escapeHtml(c.subtitle)}</span></div></div>
                $customerBlock
                <div class="tagline">${taglines.joinToString("<i>●</i>") { "<span>${escapeHtml(it)}</span>" }}</div>
                <section class="step">
                  <div class="badge"><span class="n">1</span><span class="t">${escapeHtml(c.step1Title)}</span></div>
                  <div class="row1">
                    <div class="txt"><div class="phone-ico">$PHONE</div><p>${escapeHtml(c.step1Text)}</p></div>
                    <div class="qr">$qr</div>
                  </div>
                </section>
                <section class="step">
                  <div class="badge"><span class="n">2</span><span class="t">${escapeHtml(c.step2Title)}</span></div>
                  <p>${escapeHtml(c.step2Text)}</p>
                  <div class="code-box">${escapeHtml(card.code)}</div>
                </section>
                <section class="step">
                  <div class="badge"><span class="n">3</span><span class="t">${escapeHtml(c.step3Title)}</span></div>
                  <div class="s3row">
                    <p>${escapeHtml(c.step3Text)}</p>
                    <div class="callbox">$CALL<div class="phone">${escapeHtml(c.phone)}</div></div>
                  </div>
                </section>
                <div class="foot">
                  <div>$SHIELD<span>${escapeHtml(c.foot1)}</span></div>
                  <div>$SHIELD<span>${escapeHtml(c.foot2)}</span></div>
                  <div class="wish">${escapeHtml(c.wish)}</div>
                </div>
              </article>
            </div>
            """.trimIndent()
        }
        return """<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Hướng dẫn sử dụng thẻ đo đất</title><style>$CSS</style></head><body>$pages</body></html>"""
    }

    private var activePrintWebView: WebView? = null

    fun printHtml(context: Context, html: String, jobName: String = "Huong-dan-the-NFC-CGNN") {
        val webView = WebView(context)
        activePrintWebView = webView
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter(jobName)
                val customMedia = PrintAttributes.MediaSize(
                    "CGNN_75x130",
                    "Tem 75x130mm",
                    2953, // 75mm in mils
                    5118, // 130mm in mils
                )
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(customMedia)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobName, adapter, attrs)
                activePrintWebView = null
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html; charset=utf-8", "UTF-8", null)
    }
}
