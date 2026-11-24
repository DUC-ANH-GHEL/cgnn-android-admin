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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.cgnn.admin.data.model.AdminNfcTag
import vn.cgnn.admin.data.network.AdminApiClient
import vn.cgnn.admin.nfc.NfcTagHardwareWriter
import vn.cgnn.admin.nfc.NfcWriteResult
import vn.cgnn.admin.ui.theme.LineActionButton
import vn.cgnn.admin.ui.theme.NfcColors
import vn.cgnn.admin.ui.theme.SoftActionButton
import vn.cgnn.admin.util.AdminUtils

private enum class ProgramPhase(val step: Int, val label: String) {
    PREPARING(1, "Đang tải mã thẻ..."),
    WAITING(1, "Đưa thẻ vào"),
    WRITING(2, "Đang nạp thẻ"),
    VERIFYING(3, "Đọc kiểm tra thẻ"),
    SUCCESS(4, "Xong rồi ạ"),
    ERROR(2, "Chưa nạp được"),
}

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

    var phase by remember { mutableStateOf(ProgramPhase.PREPARING) }
    var preparedUrl by remember { mutableStateOf("") }
    var hintMessage by remember { mutableStateOf("") }
    var errorTitle by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var errorAction by remember { mutableStateOf("") }
    var errorCode by remember { mutableStateOf("") }
    var errorStep by remember { mutableIntStateOf(2) }
    var lastProgrammedUid by remember { mutableStateOf("") }
    var verifiedUid by remember { mutableStateOf("") }
    var isHandlingTag by remember { mutableStateOf(false) }

    val urlCache = remember { mutableMapOf<Long, String>() }

    // Re-check NFC enabled state whenever Activity resumes (e.g. returning from NFC Settings)
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
        phase = ProgramPhase.PREPARING
        preparedUrl = ""
        hintMessage = ""
        errorTitle = ""
        errorMessage = ""
        errorAction = ""
        errorCode = ""
        verifiedUid = ""

        val cached = urlCache[tag.id]
        if (cached != null) {
            preparedUrl = cached
            phase = ProgramPhase.WAITING
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
                    phase = ProgramPhase.WAITING
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

    // Keep latest state accessible to ReaderCallback without restarting enableReaderMode!
    // Restarting enableReaderMode when `phase` changes to WRITING would call disableReaderMode()
    // mid-write and immediately cause TagLostException.
    val latestPhase by rememberUpdatedState(phase)
    val latestPreparedUrl by rememberUpdatedState(preparedUrl)
    val latestCurrentTag by rememberUpdatedState(currentTag)
    val latestLastProgrammedUid by rememberUpdatedState(lastProgrammedUid)
    val latestOnTagVerified by rememberUpdatedState(onTagVerified)
    val latestOnSendLog by rememberUpdatedState(onSendLog)

    // Native Android NFC ReaderMode stays continuously active while dialog is open & NFC is on
    DisposableEffect(activity, nfcAdapter, isNfcEnabled) {
        val canListen = activity != null && nfcAdapter != null && isNfcEnabled
        if (canListen) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            val extras = Bundle().apply {
                // Keep presence check interval comfortably longer than NDEF format/write + verify
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 2500)
            }
            val callback = NfcAdapter.ReaderCallback { nfcTag ->
                if (isHandlingTag) return@ReaderCallback
                val activePhase = latestPhase
                val url = latestPreparedUrl
                val tagItem = latestCurrentTag ?: return@ReaderCallback

                if (activePhase != ProgramPhase.WAITING && activePhase != ProgramPhase.ERROR) {
                    return@ReaderCallback
                }
                if (url.isEmpty()) return@ReaderCallback

                isHandlingTag = true
                val ignoreUidSnapshot = latestLastProgrammedUid
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            NfcTagHardwareWriter.writeAndVerify(
                                tag = nfcTag,
                                expectedUrlRaw = url,
                                ignoreUid = ignoreUidSnapshot,
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
                                verifiedUid = result.uid
                                lastProgrammedUid = result.uid
                                hintMessage = ""
                                phase = ProgramPhase.SUCCESS
                                AdminUtils.vibrateFeedback(context, success = true)
                                latestOnTagVerified(tagItem)
                                latestOnSendLog(tagItem.id, "success", result.logs)
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
            if (activity != null && nfcAdapter != null) {
                runCatching { nfcAdapter.disableReaderMode(activity) }
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAF7F0),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (queue.size > 1) "Nạp thẻ ${currentIndex + 1}/${queue.size}" else "Nạp thẻ NFC",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NfcColors.TextMuted,
                        )
                        if (currentTag != null) {
                            val titleText = listOfNotNull(
                                currentTag.codeLabel,
                                currentTag.orderRecipientName?.takeIf { it.isNotBlank() },
                            ).joinToString(" - ")
                            Text(
                                text = titleText,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.TextDeep,
                            )
                        }
                    }
                    TextButton(onClick = onClose) {
                        Text("Đóng", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = NfcColors.TextDeep)
                    }
                }

                // Order tag position badge (e.g. "Thẻ 1/2 của đơn 345")
                if (currentTag != null) {
                    val pos = currentTag.orderTagPosition
                    val total = currentTag.orderTagTotal
                    if (pos != null && total != null && total > 1 && pos in 1..total) {
                        val orderCode = currentTag.orderCode ?: currentTag.orderId?.toString()
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .background(NfcColors.AmberBg, RoundedCornerShape(10.dp))
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

                // 4-step progress bar
                val displayStep = if (phase == ProgramPhase.ERROR) errorStep else phase.step
                val isErr = phase == ProgramPhase.ERROR
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (i in 1..4) {
                        val barColor = when {
                            i < displayStep -> NfcColors.GreenForest
                            i == displayStep && isErr -> NfcColors.CoralError
                            i == displayStep -> NfcColors.GreenForest
                            else -> NfcColors.CreamBar
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor),
                        )
                    }
                }
                Text(
                    text = when {
                        nfcAdapter == null -> "Thiết bị không có NFC"
                        !isNfcEnabled -> "NFC đang tắt"
                        isErr -> errorTitle.ifBlank { "Chưa nạp được" }
                        else -> phase.label
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isErr || nfcAdapter == null || !isNfcEnabled) NfcColors.CoralError else NfcColors.GreenForest,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(Modifier.height(24.dp))

                // Main content area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when {
                        nfcAdapter == null -> {
                            StatusCircle(color = NfcColors.DangerBg, ringColor = NfcColors.CoralError, symbol = "!")
                            Text(
                                text = "Điện thoại này không có phần cứng NFC",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.TextDeep,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Bác vẫn có thể dùng các tính năng tạo thẻ, chép mã kích hoạt, chép link, in hướng dẫn và quản lý máy trên thiết bị này.",
                                fontSize = 15.sp,
                                color = NfcColors.TextMuted,
                                textAlign = TextAlign.Center,
                            )
                        }

                        !isNfcEnabled -> {
                            StatusCircle(color = NfcColors.AmberBg, ringColor = NfcColors.AmberText, symbol = "NFC")
                            Text(
                                text = "Bác cần bật NFC trên điện thoại",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.TextDeep,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Bấm nút bên dưới để mở Cài đặt NFC, bật lên rồi quay lại màn hình này.",
                                fontSize = 15.sp,
                                color = NfcColors.TextMuted,
                                textAlign = TextAlign.Center,
                            )
                            SoftActionButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Mở cài đặt NFC", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        phase == ProgramPhase.PREPARING -> {
                            CircularProgressIndicator(
                                color = NfcColors.GreenForest,
                                strokeWidth = 5.dp,
                                modifier = Modifier.size(64.dp),
                            )
                            Text(
                                text = "Đang tải mã kích hoạt từ hệ thống...",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NfcColors.TextDeep,
                                textAlign = TextAlign.Center,
                            )
                        }

                        phase == ProgramPhase.WAITING -> {
                            PulsingNfcHero()
                            Text(
                                text = "Áp thẻ NFC vào mặt sau điện thoại",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.TextDeep,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Giữ yên đến khi hiện chữ Xong rồi ạ. Thẻ trống hay đã có dữ liệu đều ghi đè được.",
                                fontSize = 15.5.sp,
                                color = NfcColors.TextMuted,
                                textAlign = TextAlign.Center,
                            )
                            if (hintMessage.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NfcColors.AmberBg, RoundedCornerShape(14.dp))
                                        .border(1.dp, NfcColors.AmberBorder, RoundedCornerShape(14.dp))
                                        .padding(14.dp),
                                ) {
                                    Text(
                                        text = hintMessage,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NfcColors.AmberText,
                                    )
                                }
                            }
                        }

                        phase == ProgramPhase.WRITING || phase == ProgramPhase.VERIFYING -> {
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(CircleShape)
                                    .background(NfcColors.CreamBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = NfcColors.GreenForest,
                                    trackColor = NfcColors.CreamBar,
                                    strokeWidth = 8.dp,
                                    modifier = Modifier.size(130.dp),
                                )
                                Text(
                                    text = "NFC",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NfcColors.GreenForest,
                                )
                            }
                            Text(
                                text = if (phase == ProgramPhase.WRITING) "Đang ghi dữ liệu vào thẻ..." else "Đang đọc kiểm tra lại thẻ...",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.TextDeep,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Bác giữ yên thẻ sát mặt sau điện thoại. Không cần bấm gì thêm ạ.",
                                fontSize = 15.5.sp,
                                color = NfcColors.TextMuted,
                                textAlign = TextAlign.Center,
                            )
                        }

                        phase == ProgramPhase.SUCCESS -> {
                            Box(
                                modifier = Modifier
                                    .size(144.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE4EDE0)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(98.dp)
                                        .clip(CircleShape)
                                        .background(NfcColors.GreenForest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("✓", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                            Text(
                                text = "Xong rồi ạ!",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.GreenForest,
                            )
                            Text(
                                text = "Đã nạp và đọc kiểm tra đúng đường dẫn của ${currentTag?.codeLabel ?: "thẻ này"}.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NfcColors.TextDeep,
                                textAlign = TextAlign.Center,
                            )
                            if (verifiedUid.isNotBlank()) {
                                Text(
                                    text = "UID chip: $verifiedUid",
                                    fontSize = 13.sp,
                                    color = NfcColors.TextMuted,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            if (currentIndex < queue.lastIndex) {
                                SoftActionButton(
                                    onClick = { currentIndex += 1 },
                                    minHeightDp = 56,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "Nạp thẻ tiếp theo (${currentIndex + 2}/${queue.size})",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            } else {
                                SoftActionButton(
                                    onClick = onClose,
                                    minHeightDp = 56,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Hoàn tất & Đóng", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        phase == ProgramPhase.ERROR -> {
                            StatusCircle(color = NfcColors.DangerBg, ringColor = NfcColors.CoralError, symbol = "!")
                            Text(
                                text = errorTitle.ifBlank { "Chưa nạp được thẻ" },
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.DangerText,
                                textAlign = TextAlign.Center,
                            )
                            if (errorMessage.isNotBlank()) {
                                Text(
                                    text = errorMessage,
                                    fontSize = 15.5.sp,
                                    color = NfcColors.TextDeep,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (errorAction.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NfcColors.AmberBg, RoundedCornerShape(14.dp))
                                        .border(1.dp, NfcColors.AmberBorder, RoundedCornerShape(14.dp))
                                        .padding(14.dp),
                                ) {
                                    Text(
                                        text = errorAction,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NfcColors.AmberText,
                                    )
                                }
                            }
                            if (errorCode.isNotBlank()) {
                                Text("Mã hỗ trợ: $errorCode", fontSize = 13.sp, color = NfcColors.TextMuted)
                            }
                            SoftActionButton(
                                onClick = {
                                    hintMessage = ""
                                    if (preparedUrl.isNotEmpty()) {
                                        phase = ProgramPhase.WAITING
                                    } else {
                                        // Trigger reload by toggling index trick or reloading directly
                                        val tag = currentTag ?: return@SoftActionButton
                                        phase = ProgramPhase.PREPARING
                                        scope.launch {
                                            try {
                                                val detail = api.getNfcTagDetail(tag.id)
                                                val u = NfcTagHardwareWriter.normalizeCgnnActivationUrl(detail?.url)
                                                if (u.isEmpty()) error("Mã kích hoạt không hợp lệ.")
                                                urlCache[tag.id] = u
                                                preparedUrl = u
                                                phase = ProgramPhase.WAITING
                                            } catch (e: Exception) {
                                                errorMessage = e.message ?: "Không tải được mã."
                                                phase = ProgramPhase.ERROR
                                            }
                                        }
                                    }
                                },
                                minHeightDp = 54,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Thử nạp lại", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                LineActionButton(
                    onClick = onClose,
                    minHeightDp = 52,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Dừng nạp", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun PulsingNfcHero() {
    val transition = rememberInfiniteTransition(label = "nfcPulse")
    val scale by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(RoundedCornerShape(42.dp))
            .background(NfcColors.CreamBg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .scale(scale)
                .border(3.dp, NfcColors.CoralError.copy(alpha = 0.65f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NfcColors.GreenForest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "NFC",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun StatusCircle(color: Color, ringColor: Color, symbol: String) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(color)
            .border(3.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontSize = if (symbol.length > 1) 24.sp else 44.sp,
            fontWeight = FontWeight.Black,
            color = ringColor,
        )
    }
}
