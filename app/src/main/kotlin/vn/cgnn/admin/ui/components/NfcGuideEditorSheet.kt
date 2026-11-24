package vn.cgnn.admin.ui.components

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import vn.cgnn.admin.data.model.NfcGuideCardData
import vn.cgnn.admin.data.model.NfcGuideContent
import vn.cgnn.admin.data.network.AdminApiClient
import vn.cgnn.admin.ui.theme.LineActionButton
import vn.cgnn.admin.ui.theme.NfcColors
import vn.cgnn.admin.ui.theme.SilverPrimaryButton
import vn.cgnn.admin.util.NfcGuideHtmlBuilder

@Composable
fun NfcGuideEditorSheet(
    api: AdminApiClient,
    onToast: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    var content by remember { mutableStateOf(NfcGuideContent.DEFAULT) }
    var taglineText by remember { mutableStateOf(NfcGuideContent.DEFAULT_TAGLINE.joinToString(" | ")) }

    LaunchedEffect(Unit) {
        loading = true
        try {
            val loaded = api.getNfcGuideContent()
            content = loaded
            taglineText = loaded.tagline.joinToString(" | ")
        } catch (_: Exception) {
            onToast("Không tải được nội dung đã lưu. Đang hiện chữ mặc định.", true)
        } finally {
            loading = false
        }
    }

    val currentContent = remember(content, taglineText) {
        content.copy(
            tagline = taglineText.split("|").map { it.trim() }.filter { it.isNotEmpty() }.take(3),
        )
    }

    AdminSheetContainer(
        title = "Sửa nội dung tờ hướng dẫn",
        busy = saving,
        onDismiss = onDismiss,
    ) {
        Text(
            text = "Gõ vào ô, tờ mẫu đổi theo ngay. Bấm Lưu thì các lần in sau dùng nội dung mới. Ô để trống sẽ dùng chữ mẫu gốc.",
            fontSize = 14.5.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                Text("Đang tải...", fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
            }
        } else {
            GuideGroup("Đầu tờ") {
                GuideField("Tiêu đề lớn", content.title) { content = content.copy(title = it.take(40)) }
                GuideField("Dòng dưới tiêu đề", content.subtitle) { content = content.copy(subtitle = it.take(60)) }
                GuideField("Chữ trước tên khách", content.forLabel) { content = content.copy(forLabel = it.take(30)) }
                GuideField("Ba ý ngắn (cách nhau bằng dấu |)", taglineText) { taglineText = it.take(100) }
            }

            GuideGroup("Bước 1") {
                GuideField("Tên bước", content.step1Title) { content = content.copy(step1Title = it.take(60)) }
                GuideField("Câu hướng dẫn", content.step1Text, singleLine = false) { content = content.copy(step1Text = it.take(220)) }
                Text("Mã QR tự tạo từ link của từng thẻ, không sửa được.", fontSize = 13.sp, color = NfcColors.TextMuted)
            }

            GuideGroup("Bước 2") {
                GuideField("Tên bước", content.step2Title) { content = content.copy(step2Title = it.take(60)) }
                GuideField("Câu hướng dẫn", content.step2Text, singleLine = false) { content = content.copy(step2Text = it.take(220)) }
                Text("Mã kích hoạt và tên khách tự điền theo từng thẻ, không sửa được.", fontSize = 13.sp, color = NfcColors.TextMuted)
            }

            GuideGroup("Bước 3") {
                GuideField("Tên bước", content.step3Title) { content = content.copy(step3Title = it.take(60)) }
                GuideField("Câu hướng dẫn", content.step3Text, singleLine = false) { content = content.copy(step3Text = it.take(220)) }
                GuideField("Số điện thoại shop", content.phone) { content = content.copy(phone = it.take(20)) }
            }

            GuideGroup("Cuối tờ") {
                GuideField("Ý thứ nhất", content.foot1) { content = content.copy(foot1 = it.take(80)) }
                GuideField("Ý thứ hai", content.foot2) { content = content.copy(foot2 = it.take(80)) }
                GuideField("Lời chúc", content.wish, singleLine = false) { content = content.copy(wish = it.take(160)) }
            }

            Spacer(Modifier.height(10.dp))
            LineActionButton(
                onClick = { showPreview = !showPreview },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showPreview) "Ẩn tờ mẫu" else "Xem tờ mẫu (75×130mm)", fontWeight = FontWeight.ExtraBold)
            }

            if (showPreview) {
                val sampleHtml = remember(currentContent) {
                    val sample = NfcGuideCardData(
                        tagId = 0L,
                        name = "Nguyễn Văn Ba",
                        code = "7966PLFU",
                        link = "https://cgnn.vn/t/mauXemTruoc1234567890",
                    )
                    NfcGuideHtmlBuilder.buildHtml(listOf(sample), currentContent)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .border(1.5.dp, Color(0xFFC3D6EA), RoundedCornerShape(14.dp))
                        .background(Color.White, RoundedCornerShape(14.dp)),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(null, sampleHtml, "text/html; charset=utf-8", "UTF-8", null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineActionButton(
                    onClick = {
                        content = NfcGuideContent.DEFAULT
                        taglineText = NfcGuideContent.DEFAULT_TAGLINE.joinToString(" | ")
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Về mẫu gốc", fontWeight = FontWeight.ExtraBold)
                }
                SilverPrimaryButton(
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                val diff = NfcGuideHtmlBuilder.diffContent(currentContent)
                                api.updateNfcGuideContent(diff)
                                onToast("Đã lưu nội dung tờ hướng dẫn.", false)
                                onDismiss()
                            } catch (e: Exception) {
                                onToast(e.message ?: "Không lưu được nội dung tờ hướng dẫn.", true)
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saving) "Đang lưu" else "Lưu", fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
                }
            }
        }
    }
}

@Composable
private fun GuideGroup(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(Color(0xFFF5F9FD), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = NfcColors.TextDeep)
        content()
    }
}

@Composable
private fun GuideField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
