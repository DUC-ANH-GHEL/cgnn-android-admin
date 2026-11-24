package vn.cgnn.admin.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.cgnn.admin.data.model.AdminNfcTag
import vn.cgnn.admin.data.model.AppFeatures
import vn.cgnn.admin.data.model.PancakeOrderStatuses
import vn.cgnn.admin.data.model.TagStatusLabels
import vn.cgnn.admin.ui.DetailExtraState
import vn.cgnn.admin.ui.theme.DangerActionButton
import vn.cgnn.admin.ui.theme.LineActionButton
import vn.cgnn.admin.ui.theme.NfcColors
import vn.cgnn.admin.ui.theme.QuickCopyPill
import vn.cgnn.admin.ui.theme.SilverPrimaryButton
import vn.cgnn.admin.ui.theme.SoftActionButton
import vn.cgnn.admin.util.AdminUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcTagDetailSheet(
    tag: AdminNfcTag,
    extra: DetailExtraState?,
    busy: Boolean,
    copying: Boolean,
    pancakeCopying: Boolean,
    showProgramButton: Boolean,
    onDismiss: () -> Unit,
    onProgram: () -> Unit,
    onAddPhone: () -> Unit,
    onMachine: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onCopyCode: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyMessage: () -> Unit,
    onEditFeatures: () -> Unit,
    onAttachOrder: () -> Unit,
    onTransfer: () -> Unit,
    onSeparateAccount: () -> Unit,
    onAttachLegacy: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nextStep = when {
        tag.status == "revoked" -> "enable"
        tag.status == "replaced" -> null
        !tag.hasCode -> "phone"
        tag.canEditMachine -> "machine"
        else -> null
    }
    val machineBtnLabel = if (tag.status == "unactivated") "Gắn máy" else "Sửa thông tin máy"

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append(tag.codeLabel)
                        if (!tag.orderRecipientName.isNullOrBlank()) {
                            append(" · ")
                            append(tag.orderRecipientName)
                        }
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = NfcColors.TextDeep,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Đóng", fontWeight = FontWeight.ExtraBold, color = NfcColors.TextMuted)
                }
            }

            // Attention banners
            if (!tag.activationStartedAt.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NfcColors.AmberBg, RoundedCornerShape(16.dp))
                        .border(1.dp, NfcColors.AmberBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Khách bắt đầu kích hoạt: ${AdminUtils.formatViDateTime(tag.activationStartedAt)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NfcColors.AmberText,
                        )
                        Text(
                            text = "Điện thoại khách dùng: ${tag.activationDeviceLabel ?: "Trình duyệt không cho biết rõ dòng máy"}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NfcColors.AmberText,
                        )
                        val osBrowser = listOfNotNull(tag.activationDeviceOs, tag.activationDeviceBrowser)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (osBrowser.isNotEmpty()) {
                            Text(osBrowser, fontSize = 13.sp, color = NfcColors.AmberText)
                        }
                        if (tag.orderRecipientPhone.isNullOrBlank() && !tag.activationPhone.isNullOrBlank()) {
                            Text("Số điện thoại: ${tag.activationPhone}", fontSize = 13.sp, color = NfcColors.AmberText)
                        }
                    }
                }
            }

            val syncErr = tag.pancakeActivationSync?.error
            if (!syncErr.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NfcColors.AmberBg, RoundedCornerShape(16.dp))
                        .border(1.dp, NfcColors.AmberBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Tin nhắn cho khách chưa gửi được.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NfcColors.AmberText,
                        )
                        Text(syncErr, fontSize = 14.sp, color = NfcColors.AmberText)
                        Text(
                            text = "Bác bấm “Chép tin nhắn” ở dưới rồi gửi tay cho khách.",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NfcColors.AmberText,
                        )
                    }
                }
            }

            // Primary actions
            if (tag.canProgram && showProgramButton) {
                SilverPrimaryButton(
                    onClick = onProgram,
                    enabled = !busy,
                    minHeightDp = 54,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Nạp vào thẻ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = NfcColors.SilverBtnText,
                    )
                }
            }

            when (nextStep) {
                "phone" -> SoftActionButton(
                    onClick = onAddPhone,
                    enabled = !busy && !copying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Thêm số điện thoại", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }

                "machine" -> SoftActionButton(
                    onClick = onMachine,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(machineBtnLabel, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }

                "enable" -> SoftActionButton(
                    onClick = onEnable,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Bật lại thẻ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            // Copy row
            if (!tag.isOff) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (tag.hasCode) {
                        QuickCopyPill(
                            onClick = onCopyCode,
                            enabled = !busy && !copying,
                            containerColor = NfcColors.BlueBg,
                            borderColor = NfcColors.BlueBorder,
                            contentColor = NfcColors.BlueText,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (copying) "Đang chép" else "Chép mã",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    QuickCopyPill(
                        onClick = onCopyLink,
                        enabled = !busy && !copying,
                        containerColor = NfcColors.EmeraldBg,
                        borderColor = NfcColors.EmeraldBorder,
                        contentColor = NfcColors.EmeraldText,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (copying) "Đang chép" else "Chép link",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    if (tag.orderId != null) {
                        QuickCopyPill(
                            onClick = onCopyMessage,
                            enabled = !busy && !pancakeCopying,
                            containerColor = NfcColors.AmberBg,
                            borderColor = NfcColors.AmberBorder,
                            contentColor = NfcColors.AmberText,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (pancakeCopying) "Đang chép" else "Tin nhắn",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }

            // Info table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NfcColors.SurfaceSoft, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                InfoRow("Tình trạng", TagStatusLabels.FULL[tag.status] ?: tag.status ?: "Chưa rõ", first = true)
                InfoRow("Máy", tag.machineLabel?.takeIf { it.isNotBlank() } ?: "Chưa gắn máy")
                InfoRow("Số điện thoại", tag.effectivePhone.ifBlank { "Chưa có" })
                val featureStr = tag.features.mapNotNull { AppFeatures.LABEL_BY_KEY[it] ?: it }.joinToString(" · ")
                InfoRow("Quyền dùng", featureStr.ifBlank { "Chưa có" })

                // Activation code
                HorizontalDivider(color = NfcColors.CardDivider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mã kích hoạt", fontSize = 14.sp, color = NfcColors.TextMuted)
                    when {
                        extra?.loading == true -> Text("Đang lấy", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
                        !extra?.activationCode.isNullOrBlank() -> Text(
                            text = extra!!.activationCode,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = NfcColors.TextDeep,
                        )
                        else -> Text("Chưa có mã", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextDeep)
                    }
                }

                // Latest login device
                HorizontalDivider(color = NfcColors.CardDivider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("Điện thoại đang dùng", fontSize = 14.sp, color = NfcColors.TextMuted)
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                        val dev = extra?.loginDevice
                        when {
                            extra?.loading == true -> Text("Đang lấy", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
                            !dev?.label.isNullOrBlank() -> {
                                Text(dev!!.label!!, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextDeep, textAlign = TextAlign.End)
                                val sub = listOfNotNull(dev.os, dev.browser).filter { it.isNotBlank() }.joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, fontSize = 12.5.sp, color = NfcColors.TextMuted)
                                if (!dev.loggedInAt.isNullOrBlank()) {
                                    Text(
                                        "Đăng nhập gần nhất: ${AdminUtils.formatViDateTime(dev.loggedInAt)}",
                                        fontSize = 12.5.sp,
                                        color = NfcColors.TextMuted,
                                    )
                                }
                            }
                            else -> {
                                Text("Chưa biết", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextDeep)
                                Text(
                                    text = if (dev != null) "Máy này không cho biết tên điện thoại" else "Khách chưa đăng nhập lại từ khi cập nhật",
                                    fontSize = 12.sp,
                                    color = NfcColors.TextMuted,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }

                // Order info
                if (tag.orderId != null) {
                    HorizontalDivider(color = NfcColors.CardDivider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("Đơn mua thẻ", fontSize = 14.sp, color = NfcColors.TextMuted)
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val orderLine = buildString {
                                append(tag.orderCode ?: "#${tag.orderId}")
                                if (!tag.orderRecipientPhone.isNullOrBlank()) {
                                    append(" · ")
                                    append(tag.orderRecipientPhone)
                                }
                            }
                            Text(orderLine, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextDeep)
                            OrderStatusBadge(tag)
                        }
                    }
                }

                // Pancake sync info
                if (tag.orderId != null && tag.pancakeActivationSync != null) {
                    val sync = tag.pancakeActivationSync
                    HorizontalDivider(color = NfcColors.CardDivider)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Tin nhắn Pancake", fontSize = 14.sp, color = NfcColors.TextMuted)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = sync.label ?: "Chưa bật tự động",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NfcColors.TextDeep,
                            )
                            if (!sync.syncedAt.isNullOrBlank()) {
                                Text(
                                    text = "Cập nhật lúc ${AdminUtils.formatViDateTime(sync.syncedAt)}",
                                    fontSize = 12.sp,
                                    color = NfcColors.TextMuted,
                                )
                            }
                        }
                    }
                }
            }

            if (tag.status == "revoked") {
                Text(
                    text = "Thẻ này đang tắt nên khách chưa dùng được. Bấm nút bật lại thẻ ở trên để dùng lại.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NfcColors.TextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEEF3F0), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                )
            } else if (tag.status == "replaced") {
                Text(
                    text = "Thẻ này đã được thay bằng thẻ khác nên không dùng nữa.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NfcColors.TextMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEEF3F0), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                )
            }

            // Other actions ("Việc khác")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NfcColors.SurfaceSoft, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "VIỆC KHÁC",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NfcColors.TextMuted,
                )

                val buttons = buildList<Pair<String, () -> Unit>> {
                    add("Sửa quyền" to onEditFeatures)
                    if (tag.status == "unactivated" && tag.orderId == null) {
                        add("Gắn vào đơn mua" to onAttachOrder)
                    }
                    if (!tag.isOff) {
                        add("Tách sang tài khoản riêng" to onSeparateAccount)
                    }
                    if (tag.status == "unactivated") {
                        add("Chuyển sang tài khoản khác" to onTransfer)
                        add("Gắn vào máy cũ" to onAttachLegacy)
                    }
                    if (tag.canEditMachine && nextStep != "machine") {
                        add(machineBtnLabel to onMachine)
                    }
                }

                buttons.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { (label, action) ->
                            LineActionButton(
                                onClick = action,
                                enabled = !busy,
                                minHeightDp = 46,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                if (!tag.isOff) {
                    DangerActionButton(
                        onClick = onDisable,
                        enabled = !busy,
                        minHeightDp = 46,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Tắt thẻ này", fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, first: Boolean = false) {
    if (!first) HorizontalDivider(color = NfcColors.CardDivider)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = NfcColors.TextMuted)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NfcColors.TextDeep,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun OrderStatusBadge(tag: AdminNfcTag) {
    val known = PancakeOrderStatuses.BY_CODE[tag.orderStatus.orEmpty()]
    val label = tag.orderStatusLabel?.takeIf { it.isNotBlank() } ?: known?.label ?: "Chưa rõ"
    val (bg, border, text) = when (known?.badgeKey) {
        "processing" -> Triple(Color(0xFFF0F9FF), Color(0xFFBAE6FD), Color(0xFF075985))
        "shipped" -> Triple(Color(0xFFEEF2FF), Color(0xFFC7D2FE), Color(0xFF3730A3))
        "delivered" -> Triple(NfcColors.EmeraldBg, NfcColors.EmeraldBorder, NfcColors.EmeraldText)
        "paid" -> Triple(Color(0xFFF0FDF4), Color(0xFFBBF7D0), Color(0xFF166534))
        "cancelled" -> Triple(NfcColors.DangerBg, NfcColors.DangerBorder, NfcColors.DangerText)
        else -> Triple(NfcColors.AmberBg, NfcColors.AmberBorder, NfcColors.AmberText)
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("Trạng thái đơn: $label", fontSize = 12.sp, fontWeight = FontWeight.Black, color = text)
    }
}
