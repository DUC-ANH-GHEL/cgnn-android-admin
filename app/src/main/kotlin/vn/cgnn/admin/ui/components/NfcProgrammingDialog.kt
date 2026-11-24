package vn.cgnn.admin.ui.components

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.cgnn.admin.data.model.AdminNfcTag
import vn.cgnn.admin.data.model.TagStatusLabels
import vn.cgnn.admin.data.network.AdminApiClient
import vn.cgnn.admin.nfc.NfcTagHardwareWriter
import vn.cgnn.admin.nfc.NfcWriteResult
import vn.cgnn.admin.ui.theme.NfcColors
import vn.cgnn.admin.util.AdminUtils

private enum class ProgramPhase(val step: Int, val stepText: String) {
    READY(1, "Đưa thẻ vào"),
    PREPARING(1, "Đưa thẻ vào"),
    WAITING(1, "Đưa thẻ vào"),
    WAITING_NEXT(1, "Đưa thẻ vào"),
    WRITING(2, "Đang nạp thẻ"),
    VERIFYING(3, "Đọc kiểm tra thẻ"),
    SUCCESS(4, "Xong rồi ạ"),
    ERROR(2, "Chưa nạp được"),
}

private val BgCream = Color(0xFFF7F3EA)
private val CardCream = Color(0xFFFFFDF8)
private val BorderCream = Color(0xFFEADFD4)
private val TextInk = Color(0xFF2B2A22)
private val TextWarmMuted = Color(0xFF6B6459)
private val TextWarmLabel = Color(0xFF8A7F70)
private val ForestGreen = Color(0xFF31543B)
private val ForestSoftBg = Color(0xFFE4EDE0)
private val ForestSoftText = Color(0xFF27442F)
private val ErrorRed = Color(0xFFB4432E)
private val ErrorCardBg = Color(0xFFFFF3EE)
private val ErrorCardBorder = Color(0xFFF2C5B6)
private val OutlineBtnBorder = Color(0xFFD9CCB8)
private val OutlineBtnText = Color(0xFF4A4639)

@Composable
fun NfcProgrammingDialog(
    queue: List<AdminNfcTag>,
    api: AdminApiClient,
    onTagVerified: (AdminNfcTag) -> Unit,
    onSendLog: (tagId: Long?, outcome: String, entries: List<String>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var isNfcEnabled by remember { mutableStateOf(nfcAdapter?.isEnabled == true) }

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentTag = queue.getOrNull(currentIndex)
    val isLastTag = currentIndex >= queue.lastIndex

    var phase by remember { mutableStateOf(ProgramPhase.PREPARING) }
    var preparedUrl by remember { mutableStateOf("") }
    var hintMessage by remember { mutableStateOf("") }
    var errorTitle by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var errorAction by remember { mutableStateOf("") }
    var errorCode by remember { mutableStateOf("") }
    var errorStep by remember { mutableIntStateOf(2) }
    var lastProgrammedUid by remember { mutableStateOf("") }
    var lastCompletedCode by remember { mutableStateOf("") }
    var completedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isHandlingTag by remember { mutableStateOf(false) }

    val urlCache = remember { mutableMapOf<Long, String>() }

    // Re-check NFC enabled state whenever Activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNfcEnabled = nfcAdapter?.isEnabled == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load activation URL for currentTag (and prefetch next tag in queue)
    LaunchedEffect(currentIndex, queue) {
        val tag = queue.getOrNull(currentIndex) ?: return@LaunchedEffect
        val nextWaitingPhase = if (currentIndex > 0) ProgramPhase.WAITING_NEXT else ProgramPhase.WAITING
        if (!isHandlingTag) {
            phase = ProgramPhase.PREPARING
            preparedUrl = ""
            hintMessage = ""
            errorTitle = ""
            errorMessage = ""
            errorAction = ""
            errorCode = ""

            val cached = urlCache[tag.id]
            if (cached != null) {
                preparedUrl = cached
                phase = nextWaitingPhase
            } else {
                try {
                    val detail = api.getNfcTagDetail(tag.id)
                    val normalized = NfcTagHardwareWriter.normalizeCgnnActivationUrl(detail?.url)
                    if (normalized.isEmpty()) {
                        errorTitle = "Mã kích hoạt không hợp lệ"
                        errorMessage = "Mã kích hoạt của thẻ không hợp lệ hoặc đã bị thiếu."
                        errorAction = "Bác kiểm tra lại thẻ này trên hệ thống."
                        errorCode = "NFC-URL-THIEU"
                        errorStep = 1
                        phase = ProgramPhase.ERROR
                    } else {
                        urlCache[tag.id] = normalized
                        preparedUrl = normalized
                        phase = nextWaitingPhase
                    }
                } catch (e: Exception) {
                    errorTitle = "Chưa tải được mã kích hoạt"
                    errorMessage = e.message ?: "CGNN chưa tải được mã kích hoạt của thẻ này từ hệ thống."
                    errorAction = "Bác kiểm tra mạng rồi bấm Thử lại."
                    errorCode = "NFC-TAI-MA"
                    errorStep = 1
                    phase = ProgramPhase.ERROR
                }
            }
        }

        // Prefetch next tag URL silently
        val nextTag = queue.getOrNull(currentIndex + 1)
        if (nextTag != null && !urlCache.containsKey(nextTag.id)) {
            launch {
                runCatching {
                    val d = api.getNfcTagDetail(nextTag.id)
                    val u = NfcTagHardwareWriter.normalizeCgnnActivationUrl(d?.url)
                    if (u.isNotEmpty()) urlCache[nextTag.id] = u
                }
            }
        }
    }

    // Keep latest state accessible to ReaderCallback without restarting enableReaderMode
    val latestPhase by rememberUpdatedState(phase)
    val latestPreparedUrl by rememberUpdatedState(preparedUrl)
    val latestCurrentTag by rememberUpdatedState(currentTag)
    val latestCurrentIndex by rememberUpdatedState(currentIndex)
    val latestLastProgrammedUid by rememberUpdatedState(lastProgrammedUid)
    val latestOnTagVerified by rememberUpdatedState(onTagVerified)
    val latestOnSendLog by rememberUpdatedState(onSendLog)
    val latestOnClose by rememberUpdatedState(onClose)

    var removalWatchJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(activity, nfcAdapter, isNfcEnabled) {
        val canListen = activity != null && nfcAdapter != null && isNfcEnabled
        if (canListen) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            val extras = Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 2500)
            }
            val callback = NfcAdapter.ReaderCallback { nfcTag ->
                if (isHandlingTag) return@ReaderCallback
                val activePhase = latestPhase
                val activeIdx = latestCurrentIndex
                val prevUid = latestLastProgrammedUid

                var targetIdx = activeIdx
                var tagItem = latestCurrentTag ?: return@ReaderCallback
                var url = latestPreparedUrl

                if (activePhase == ProgramPhase.SUCCESS) {
                    val tappedUid = NfcTagHardwareWriter.extractUid(nfcTag)
                    if (prevUid.isNotBlank() && tappedUid.equals(prevUid, ignoreCase = true)) {
                        return@ReaderCallback
                    }
                    if (activeIdx >= queue.lastIndex) {
                        return@ReaderCallback
                    }
                    removalWatchJob?.cancel()
                    removalWatchJob = null
                    val nextIdx = activeIdx + 1
                    val nextTag = queue.getOrNull(nextIdx) ?: return@ReaderCallback
                    val nextCachedUrl = urlCache[nextTag.id]
                    if (nextCachedUrl.isNullOrEmpty()) {
                        scope.launch(Dispatchers.Main) { currentIndex = nextIdx }
                        return@ReaderCallback
                    }
                    targetIdx = nextIdx
                    tagItem = nextTag
                    url = nextCachedUrl
                    scope.launch(Dispatchers.Main) {
                        currentIndex = nextIdx
                        preparedUrl = nextCachedUrl
                    }
                } else if (activePhase != ProgramPhase.WAITING &&
                    activePhase != ProgramPhase.WAITING_NEXT &&
                    activePhase != ProgramPhase.ERROR
                ) {
                    return@ReaderCallback
                }

                if (url.isEmpty()) return@ReaderCallback

                isHandlingTag = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            NfcTagHardwareWriter.writeAndVerify(
                                tag = nfcTag,
                                expectedUrlRaw = url,
                                ignoreUid = prevUid,
                                onStageChange = { stage ->
                                    scope.launch(Dispatchers.Main) {
                                        if (stage == "writing") phase = ProgramPhase.WRITING
                                        else if (stage == "verifying") phase = ProgramPhase.VERIFYING
                                    }
                                },
                            )
                        }
                        when (result) {
                            is NfcWriteResult.IgnoredPreviousTag -> {
                                hintMessage = "Thẻ này vừa nạp xong rồi. Bác nhấc nó ra xa và đưa thẻ mới vào nhé."
                                AdminUtils.vibrateFeedback(context, success = false)
                            }

                            is NfcWriteResult.Success -> {
                                lastProgrammedUid = result.uid
                                lastCompletedCode = tagItem.codeLabel
                                completedTagIds = completedTagIds + tagItem.id
                                hintMessage = ""
                                phase = ProgramPhase.SUCCESS
                                AdminUtils.vibrateFeedback(context, success = true)
                                latestOnTagVerified(tagItem)
                                latestOnSendLog(tagItem.id, "success", result.logs)

                                val completedIdx = targetIdx
                                val successShownAt = System.currentTimeMillis()
                                removalWatchJob?.cancel()
                                removalWatchJob = scope.launch {
                                    val removed = withContext(Dispatchers.IO) {
                                        NfcTagHardwareWriter.waitUntilTagRemoved(nfcTag)
                                    }
                                    if (!removed) return@launch
                                    val elapsed = System.currentTimeMillis() - successShownAt
                                    if (elapsed < 850L) delay(850L - elapsed)
                                    if (latestPhase == ProgramPhase.SUCCESS && latestCurrentIndex == completedIdx) {
                                        if (completedIdx < queue.lastIndex) {
                                            currentIndex = completedIdx + 1
                                        } else {
                                            latestOnClose()
                                        }
                                    }
                                }
                            }

                            is NfcWriteResult.Error -> {
                                errorTitle = result.title
                                errorMessage = result.message
                                errorAction = result.action
                                errorCode = result.code
                                errorStep = if (result.stage == "verify") 3 else 2
                                phase = ProgramPhase.ERROR
                                AdminUtils.vibrateFeedback(context, success = false)
                                latestOnSendLog(tagItem.id, "error", result.logs + "Mã lỗi: ${result.code}")
                            }
                        }
                    } finally {
                        isHandlingTag = false
                    }
                }
            }
            runCatching {
                nfcAdapter.enableReaderMode(activity, callback, flags, extras)
            }
        }
        onDispose {
            removalWatchJob?.cancel()
            removalWatchJob = null
            if (activity != null && nfcAdapter != null) {
                runCatching { nfcAdapter.disableReaderMode(activity) }
            }
        }
    }

    if (currentTag == null) return

    val step = if (phase == ProgramPhase.ERROR) errorStep else phase.step
    val stepTitle = if (phase == ProgramPhase.ERROR) {
        when (errorStep) {
            1 -> "Chưa bắt đầu được"
            2 -> "Chưa nạp được"
            3 -> "Chưa kiểm tra được"
            else -> "Chưa chuyển được sang thẻ tiếp theo"
        }
    } else {
        phase.stepText
    }
    val tagStatusText = TagStatusLabels.FULL[currentTag.status] ?: "Chưa kích hoạt"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BgCream,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Top header bar (matching web)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Nạp thẻ ${currentIndex + 1}/${queue.size}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ForestGreen,
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardCream)
                            .border(1.dp, BorderCream, RoundedCornerShape(16.dp))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        CloseVectorIcon(color = TextInk, size = 20.dp)
                    }
                }

                // "Thẻ đang nạp" card (exact web match)
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(CardCream)
                        .border(1.dp, BorderCream, RoundedCornerShape(28.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "Thẻ đang nạp",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWarmLabel,
                    )
                    Text(
                        text = currentTag.codeLabel,
                        fontSize = 29.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextInk,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // Dashed divider
                    Canvas(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .height(2.dp),
                    ) {
                        drawLine(
                            color = BorderCream,
                            start = Offset(0f, 1f),
                            end = Offset(size.width, 1f),
                            strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentTag.orderRecipientName.orEmpty(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextInk,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (phase == ProgramPhase.SUCCESS) ForestSoftBg else Color(0xFFF3ECE0))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = if (phase == ProgramPhase.SUCCESS) "Đã nạp đúng" else tagStatusText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (phase == ProgramPhase.SUCCESS) ForestSoftText else TextWarmMuted,
                            )
                        }
                    }

                    // Order tag position badge
                    val pos = currentTag.orderTagPosition
                    val total = currentTag.orderTagTotal
                    if (pos != null && total != null && total > 1 && pos in 1..total) {
                        val orderCode = currentTag.orderCode ?: currentTag.orderId?.toString()
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NfcColors.AmberBg)
                                .border(1.dp, NfcColors.AmberBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Thẻ $pos/$total của đơn${if (!orderCode.isNullOrBlank()) " $orderCode" else " này"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.AmberText,
                            )
                        }
                    }
                }

                // Queue progress chip (when > 1 tag)
                if (queue.size > 1) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ForestSoftBg)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Đã nạp ${completedTagIds.size}/${queue.size} thẻ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestSoftText,
                        )
                    }
                }

                // Step header & 4-segment bar
                if (nfcAdapter != null && isNfcEnabled) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 16.dp),
                    ) {
                        Text(
                            text = "Bước $step/4 · $stepTitle",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (phase == ProgramPhase.ERROR) ErrorRed else ForestGreen,
                        )
                        Row(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (i in 1..4) {
                                val modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                when {
                                    i < step -> Box(modifier.background(ForestGreen))
                                    i == step && phase == ProgramPhase.ERROR -> Box(modifier.background(NfcColors.CoralError))
                                    i == step && (step == 1 || step == 4) -> Box(modifier.background(ForestGreen))
                                    i == step -> Box(
                                        modifier.background(
                                            Brush.horizontalGradient(
                                                0.0f to ForestGreen,
                                                0.55f to ForestGreen,
                                                0.551f to NfcColors.CreamBar,
                                                1.0f to NfcColors.CreamBar,
                                            ),
                                        ),
                                    )
                                    else -> Box(modifier.background(NfcColors.CreamBar))
                                }
                            }
                        }
                    }
                }

                // Middle scrollable stage area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when {
                        nfcAdapter == null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color(0xFFFFF8E6))
                                    .border(2.dp, Color(0xFFF2D9A6), RoundedCornerShape(28.dp))
                                    .padding(20.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Điện thoại này chưa nạp trực tiếp được",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF5C3D0B),
                                    )
                                    Text(
                                        text = "Thiết bị không có phần cứng NFC. Bác vẫn dùng được các tính năng tạo thẻ, chép mã, in hướng dẫn và quản lý máy.",
                                        fontSize = 16.sp,
                                        color = Color(0xFF5C3D0B),
                                    )
                                }
                            }
                        }

                        !isNfcEnabled -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color(0xFFFFF8E6))
                                    .border(2.dp, Color(0xFFF2D9A6), RoundedCornerShape(28.dp))
                                    .padding(20.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "Bác cần bật NFC trên điện thoại",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF5C3D0B),
                                    )
                                    Text(
                                        text = "Bấm nút bên dưới để mở Cài đặt NFC, bật lên rồi quay lại màn hình này.",
                                        fontSize = 16.sp,
                                        color = Color(0xFF5C3D0B),
                                    )
                                    ForestPrimaryButton(
                                        text = "Mở cài đặt NFC",
                                        onClick = {
                                            runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                                        },
                                    )
                                }
                            }
                        }

                        phase == ProgramPhase.ERROR -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(ErrorCardBg)
                                    .border(2.dp, ErrorCardBorder, RoundedCornerShape(28.dp))
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        WarningVectorIcon(color = ErrorRed, size = 26.dp)
                                    }
                                    Text(
                                        text = errorTitle.ifBlank { "Chưa nạp được thẻ" },
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF7A2A1A),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (errorMessage.isNotBlank()) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Vì sao: ") }
                                            append(errorMessage)
                                        },
                                        fontSize = 16.5.sp,
                                        lineHeight = 24.sp,
                                        color = Color(0xFF4A2A22),
                                    )
                                }
                                if (errorAction.isNotBlank()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White)
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = "Bác làm như sau nhé",
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ForestSoftText,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(ForestGreen),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("1", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                            }
                                            Text(
                                                text = errorAction,
                                                fontSize = 16.sp,
                                                lineHeight = 23.sp,
                                                color = TextInk,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                                if (errorCode.isNotBlank()) {
                                    Text(
                                        text = "Mã hỗ trợ: $errorCode",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF8A6D63),
                                    )
                                }
                            }
                        }

                        phase == ProgramPhase.SUCCESS -> {
                            DoneHeroWeb()
                            Spacer(Modifier.height(22.dp))
                            Text(
                                text = "Đã nạp và kiểm tra đúng thẻ",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextInk,
                                textAlign = TextAlign.Center,
                                lineHeight = 32.sp,
                            )
                            Text(
                                text = if (isLastTag) {
                                    "Bác nhấc thẻ ra khỏi điện thoại là xong ạ."
                                } else {
                                    "Bác nhấc thẻ ra để nạp thẻ tiếp theo ạ."
                                },
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextWarmMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            // Web preview card ("Khách quét thẻ sẽ thấy trang kích hoạt máy")
                            Row(
                                modifier = Modifier
                                    .padding(top = 20.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(CardCream)
                                    .border(1.dp, BorderCream, RoundedCornerShape(22.dp))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(46.dp)
                                        .height(62.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ForestGreen),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                                ) {
                                    Box(Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF2B09F)))
                                    Box(Modifier.width(20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.55f)))
                                    Box(Modifier.width(24.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.55f)))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Khách quét thẻ sẽ thấy trang kích hoạt máy",
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextInk,
                                        lineHeight = 21.sp,
                                    )
                                    Text(
                                        text = "Mã đọc lại khớp với mã đã nạp.",
                                        fontSize = 14.sp,
                                        color = TextWarmMuted,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                }
                            }
                        }

                        phase == ProgramPhase.WRITING || phase == ProgramPhase.VERIFYING -> {
                            SpinHeroWeb()
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = if (phase == ProgramPhase.WRITING) "Đang nạp thẻ" else "Đang đọc kiểm tra thẻ",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextInk,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Bác giữ yên thẻ sát mặt sau điện thoại. Không cần bấm gì thêm ạ.",
                                fontSize = 16.5.sp,
                                color = TextWarmMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        else -> {
                            // READY / PREPARING / WAITING / WAITING_NEXT
                            WaitHeroWeb()
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = when (phase) {
                                    ProgramPhase.WAITING_NEXT -> "Đưa thẻ tiếp theo vào để nạp"
                                    ProgramPhase.PREPARING -> "Đang chuẩn bị mã..."
                                    else -> "Bác áp thẻ vào mặt sau điện thoại"
                                },
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextInk,
                                textAlign = TextAlign.Center,
                                lineHeight = 32.sp,
                            )
                            Text(
                                text = buildAnnotatedString {
                                    if (hintMessage.isNotBlank()) {
                                        append(hintMessage)
                                    } else {
                                        append("Giữ yên đến khi hiện chữ ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = TextInk)) {
                                            append("Xong rồi ạ")
                                        }
                                        append(". Thẻ trống hay đã có dữ liệu đều ghi đè được.")
                                    }
                                },
                                fontSize = 16.5.sp,
                                color = TextWarmMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            if (lastCompletedCode.isNotBlank() && phase == ProgramPhase.WAITING_NEXT) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 14.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(ForestSoftBg)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = "Đã nạp xong $lastCompletedCode",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestSoftText,
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom action area (exact web parity)
                if (nfcAdapter != null && isNfcEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 22.dp, top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (phase) {
                            ProgramPhase.ERROR -> {
                                ForestPrimaryButton(
                                    text = if (errorStep == 3) "Nạp lại và kiểm tra" else "Thử lại",
                                    onClick = {
                                        hintMessage = ""
                                        if (preparedUrl.isNotEmpty()) {
                                            phase = ProgramPhase.WAITING
                                        } else {
                                            phase = ProgramPhase.PREPARING
                                            scope.launch {
                                                try {
                                                    val detail = api.getNfcTagDetail(currentTag.id)
                                                    val u = NfcTagHardwareWriter.normalizeCgnnActivationUrl(detail?.url)
                                                    if (u.isEmpty()) error("Mã kích hoạt không hợp lệ.")
                                                    urlCache[currentTag.id] = u
                                                    preparedUrl = u
                                                    phase = ProgramPhase.WAITING
                                                } catch (e: Exception) {
                                                    errorMessage = e.message ?: "Không tải được mã."
                                                    phase = ProgramPhase.ERROR
                                                }
                                            }
                                        }
                                    },
                                )
                            }

                            ProgramPhase.SUCCESS -> {
                                if (isLastTag) {
                                    ForestPrimaryButton(
                                        text = "Hoàn tất",
                                        onClick = {
                                            removalWatchJob?.cancel()
                                            removalWatchJob = null
                                            onClose()
                                        },
                                    )
                                } else {
                                    CreamOutlineButton(
                                        text = "Nạp thẻ tiếp theo",
                                        onClick = {
                                            removalWatchJob?.cancel()
                                            removalWatchJob = null
                                            currentIndex += 1
                                        },
                                    )
                                    Text(
                                        text = "Máy chưa tự chuyển thì bấm nút này.",
                                        fontSize = 14.sp,
                                        color = TextWarmMuted,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }

                            ProgramPhase.READY -> {
                                ForestPrimaryButton(
                                    text = "Bắt đầu nạp thẻ này",
                                    enabled = preparedUrl.isNotEmpty(),
                                    onClick = { phase = ProgramPhase.WAITING },
                                )
                            }

                            else -> {
                                CreamOutlineButton(
                                    text = "Dừng nạp",
                                    onClick = { phase = ProgramPhase.READY },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForestPrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ForestGreen,
            contentColor = Color.White,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
    ) {
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun CreamOutlineButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(2.dp, OutlineBtnBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CardCream,
            contentColor = OutlineBtnText,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WaitHeroWeb() {
    val transition = rememberInfiniteTransition(label = "nfcPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = Modifier
            .width(196.dp)
            .height(186.dp)
            .clip(RoundedCornerShape(44.dp))
            .background(NfcColors.CreamBg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(146.dp)
                .scale(pulseScale)
                .border(3.dp, NfcColors.CoralError.copy(alpha = pulseAlpha), CircleShape),
        )
        Canvas(modifier = Modifier.width(130.dp).height(148.dp)) {
            val sx = size.width / 130f
            val sy = size.height / 150f
            // Phone body
            drawRoundRect(
                color = ForestGreen,
                topLeft = Offset(24f * sx, 6f * sy),
                size = Size(82f * sx, 138f * sy),
                cornerRadius = CornerRadius(18f * sx, 18f * sy),
            )
            // Camera lens dot
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = 6f * sx,
                center = Offset(44f * sx, 26f * sy),
            )
            // Tilted NFC card
            rotate(degrees = -12f, pivot = Offset(66f * sx, 80f * sy)) {
                drawRoundRect(
                    color = CardCream,
                    topLeft = Offset(34f * sx, 62f * sy),
                    size = Size(70f * sx, 44f * sy),
                    cornerRadius = CornerRadius(10f * sx, 10f * sy),
                )
                drawRoundRect(
                    color = NfcColors.CoralError,
                    topLeft = Offset(34f * sx, 62f * sy),
                    size = Size(70f * sx, 44f * sy),
                    cornerRadius = CornerRadius(10f * sx, 10f * sy),
                    style = Stroke(width = 3f * sx),
                )
                val waveStroke = Stroke(width = 3f * sx, cap = StrokeCap.Round)
                val w1 = Path().apply {
                    moveTo(60f * sx, 76f * sy)
                    quadraticTo(69f * sx, 85f * sy, 60f * sx, 94f * sy)
                }
                val w2 = Path().apply {
                    moveTo(67f * sx, 72f * sy)
                    quadraticTo(80f * sx, 85f * sy, 67f * sx, 98f * sy)
                }
                drawPath(w1, color = NfcColors.CoralError, style = waveStroke)
                drawPath(w2, color = NfcColors.CoralError, style = waveStroke)
            }
        }
    }
}

@Composable
private fun SpinHeroWeb() {
    Box(
        modifier = Modifier
            .size(156.dp)
            .clip(CircleShape)
            .background(NfcColors.CreamBg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = ForestGreen,
            trackColor = NfcColors.CreamBar,
            strokeWidth = 8.dp,
            modifier = Modifier.size(156.dp),
        )
        NfcWaveVectorIcon(color = ForestGreen, size = 56.dp)
    }
}

@Composable
private fun DoneHeroWeb() {
    Box(
        modifier = Modifier
            .size(148.dp)
            .clip(CircleShape)
            .background(ForestSoftBg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(102.dp)
                .clip(CircleShape)
                .background(ForestGreen),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", fontSize = 52.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}
