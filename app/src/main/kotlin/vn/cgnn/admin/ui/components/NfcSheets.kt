package vn.cgnn.admin.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import vn.cgnn.admin.data.model.AdminNfcTag
import vn.cgnn.admin.data.model.AppFeatures
import vn.cgnn.admin.data.model.PancakeOrderStatuses
import vn.cgnn.admin.data.model.TagStatusLabels
import vn.cgnn.admin.ui.theme.DangerActionButton
import vn.cgnn.admin.ui.theme.LineActionButton
import vn.cgnn.admin.ui.theme.NfcColors
import vn.cgnn.admin.ui.theme.SilverPrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSheetContainer(
    title: String,
    busy: Boolean = false,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = NfcColors.TextDeep,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Đóng", fontWeight = FontWeight.ExtraBold, color = NfcColors.TextMuted)
                }
            }
            content()
        }
    }
}

@Composable
fun StatusFilterSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AdminSheetContainer(title = "Lọc theo tình trạng thẻ", onDismiss = onDismiss) {
        Text(
            text = "Bác chọn một mục, danh sách sẽ chỉ còn các thẻ phù hợp.",
            fontSize = 15.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        TagStatusLabels.FILTER.forEachIndexed { idx, (value, label) ->
            if (idx > 0) HorizontalDivider(color = NfcColors.CardDivider)
            val fullText = if (value.isNotEmpty()) "$label (${TagStatusLabels.FULL[value] ?: value})" else label
            SelectableOptionRow(
                label = fullText,
                selected = current == value,
                onClick = {
                    onSelect(value)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
fun OrderFilterSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember {
        buildList {
            add("" to "Tất cả đơn")
            add("without_order" to "Thẻ chưa có đơn mua")
            PancakeOrderStatuses.ALL.forEach { add(it.code to it.label) }
        }
    }
    AdminSheetContainer(title = "Lọc theo đơn mua thẻ", onDismiss = onDismiss) {
        Text(
            text = "Bác chọn một mục, danh sách sẽ chỉ còn các thẻ phù hợp.",
            fontSize = 15.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        options.forEachIndexed { idx, (value, label) ->
            if (idx > 0) HorizontalDivider(color = NfcColors.CardDivider)
            SelectableOptionRow(
                label = label,
                selected = current == value,
                onClick = {
                    onSelect(value)
                    onDismiss()
                },
            )
        }
    }
}

fun todayVietnamDateIso(): String =
    LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString()

fun yesterdayVietnamDateIso(): String =
    LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1).toString()

fun formatDateShortVi(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(iso)

@Composable
fun StatusDateFilterSheet(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val today = remember { todayVietnamDateIso() }
    val yesterday = remember { yesterdayVietnamDateIso() }
    val presets = remember(today, yesterday) {
        listOf(
            "" to "Tất cả ngày",
            today to "Hôm nay (${formatDateShortVi(today)})",
            yesterday to "Hôm qua (${formatDateShortVi(yesterday)})",
        )
    }

    AdminSheetContainer(title = "Lọc theo ngày cập nhật trạng thái", onDismiss = onDismiss) {
        Text(
            text = "Chọn ngày đơn mua thẻ được cập nhật trạng thái.",
            fontSize = 15.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        presets.forEachIndexed { idx, (value, label) ->
            if (idx > 0) HorizontalDivider(color = NfcColors.CardDivider)
            SelectableOptionRow(
                label = label,
                selected = current == value,
                onClick = {
                    onSelect(value)
                    onDismiss()
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("Hoặc chọn một ngày", fontSize = 14.sp, fontWeight = FontWeight.Black, color = NfcColors.TextDeep)
        Spacer(Modifier.height(8.dp))
        LineActionButton(
            onClick = {
                val initDate = runCatching { LocalDate.parse(current) }
                    .getOrElse { LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")) }
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val picked = LocalDate.of(year, month + 1, dayOfMonth).toString()
                        onSelect(picked)
                    },
                    initDate.year,
                    initDate.monthValue - 1,
                    initDate.dayOfMonth,
                ).show()
            },
            minHeightDp = 54,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (current.isNotBlank()) "Ngày đang chọn: ${formatDateShortVi(current)}" else "Mở lịch chọn ngày...",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))
        SilverPrimaryButton(
            onClick = onDismiss,
            minHeightDp = 54,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Xem thẻ", fontSize = 16.5.sp, fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
        }
    }
}

@Composable
private fun SelectableOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.5.sp,
            fontWeight = FontWeight.Bold,
            color = NfcColors.TextDeep,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Black, color = NfcColors.DarkBarStart)
        }
    }
}

@Composable
fun ToolsSheet(
    busy: Boolean,
    bulkPancakeSyncing: Boolean,
    bulkPancakeProgress: String,
    selectedCount: Int,
    defaultFeatures: List<String>,
    defaultFeaturesLoading: Boolean,
    onCopyTrialLink: () -> Unit,
    onSyncAllPancake: () -> Unit,
    onCreateFullAccessCodes: () -> Unit,
    onExportCsv: () -> Unit,
    onOpenGuideEditor: () -> Unit,
    onOpenButtonSettings: () -> Unit,
    onOpenDefaultFeatures: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmFullAccess by remember { mutableStateOf(false) }

    AdminSheetContainer(title = "Thêm việc", busy = bulkPancakeSyncing, onDismiss = onDismiss) {
        if (confirmFullAccess) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tạo 12 mã kích hoạt đủ mọi quyền, không gắn đơn, rồi tải/chia sẻ file JSON về máy?",
                fontSize = 15.5.sp,
                color = NfcColors.TextDeep,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineActionButton(onClick = { confirmFullAccess = false }, modifier = Modifier.weight(1f)) {
                    Text("Để sau", fontWeight = FontWeight.ExtraBold)
                }
                SilverPrimaryButton(
                    onClick = {
                        confirmFullAccess = false
                        onDismiss()
                        onCreateFullAccessCodes()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Tạo 12 mã", fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
                }
            }
            return@AdminSheetContainer
        }

        Spacer(Modifier.height(6.dp))
        ToolMenuItem(
            title = "Sao chép đo đất dùng thử",
            subtitle = "Gửi cho khách dùng thử, chưa cần thẻ",
            enabled = true,
            onClick = {
                onDismiss()
                onCopyTrialLink()
            },
        )
        HorizontalDivider(color = NfcColors.CardDivider)
        ToolMenuItem(
            title = if (bulkPancakeSyncing) bulkPancakeProgress.ifBlank { "Đang đồng bộ..." } else "Đồng bộ tất cả sang Pancake",
            subtitle = "Gửi tin nhắn cho các thẻ còn thiếu, làm một lần",
            enabled = !busy && !bulkPancakeSyncing,
            loading = bulkPancakeSyncing,
            onClick = onSyncAllPancake,
        )
        HorizontalDivider(color = NfcColors.CardDivider)
        ToolMenuItem(
            title = "Tạo 12 mã kích hoạt full quyền (tải file)",
            subtitle = "Tạo xong mở chia sẻ / lưu file JSON về máy",
            enabled = !busy && !bulkPancakeSyncing,
            onClick = { confirmFullAccess = true },
        )
        HorizontalDivider(color = NfcColors.CardDivider)
        ToolMenuItem(
            title = if (selectedCount > 0) "Tải $selectedCount thẻ đã chọn" else "Tải file đang lọc",
            subtitle = "File CSV để nạp thẻ bằng máy tính",
            enabled = !busy && !bulkPancakeSyncing,
            onClick = {
                onDismiss()
                onExportCsv()
            },
        )
        HorizontalDivider(color = NfcColors.CardDivider)
        ToolMenuItem(
            title = "Sửa nội dung tờ hướng dẫn",
            subtitle = "Sửa chữ trên tờ in, lưu chung cho cả shop",
            enabled = true,
            onClick = {
                onDismiss()
                onOpenGuideEditor()
            },
        )
        HorizontalDivider(color = NfcColors.CardDivider)
        ToolMenuItem(
            title = "Ẩn hiện nút trên màn hình",
            subtitle = "Chọn nút Nạp thẻ NFC và In hướng dẫn",
            enabled = true,
            onClick = {
                onDismiss()
                onOpenButtonSettings()
            },
        )
        HorizontalDivider(color = NfcColors.CardDivider)
        val featureSummary = if (defaultFeaturesLoading) {
            "Đang tải..."
        } else {
            AppFeatures.ALL.filter { it.key in defaultFeatures }.joinToString(" · ") { it.label }
                .ifBlank { "Chưa chọn quyền" }
        }
        ToolMenuItem(
            title = "Quyền mặc định cho thẻ mới",
            subtitle = featureSummary,
            enabled = true,
            onClick = {
                onDismiss()
                onOpenDefaultFeatures()
            },
        )
        HorizontalDivider(color = NfcColors.CardDivider)
        ToolMenuItem(
            title = "Đăng xuất tài khoản quản trị",
            subtitle = "Thoát phiên hiện tại trên điện thoại này",
            enabled = !busy && !bulkPancakeSyncing,
            titleColor = NfcColors.DangerText,
            onClick = {
                onDismiss()
                onLogout()
            },
        )
    }
}

@Composable
private fun ToolMenuItem(
    title: String,
    subtitle: String,
    enabled: Boolean,
    loading: Boolean = false,
    titleColor: Color = NfcColors.TextDeep,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) titleColor else NfcColors.TextSubtle,
            )
            Text(
                text = subtitle,
                fontSize = 13.5.sp,
                color = NfcColors.TextMuted,
            )
        }
    }
}

@Composable
fun ButtonSettingsSheet(
    showProgramButton: Boolean,
    showGuideButton: Boolean,
    onToggleProgram: (Boolean) -> Unit,
    onToggleGuide: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AdminSheetContainer(title = "Ẩn hiện nút trên ứng dụng", onDismiss = onDismiss) {
        Text(
            text = "Tích vào ô để nút hiện trên điện thoại này.",
            fontSize = 15.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        FeatureCheckCard(
            label = "Nạp thẻ NFC (trên từng thẻ, chi tiết và thanh chọn)",
            checked = showProgramButton,
            onCheckedChange = onToggleProgram,
        )
        Spacer(Modifier.height(8.dp))
        FeatureCheckCard(
            label = "In hướng dẫn (trên thanh chọn thẻ)",
            checked = showGuideButton,
            onCheckedChange = onToggleGuide,
        )
    }
}

@Composable
fun FeaturesEditorSheet(
    title: String,
    description: String,
    initialFeatures: List<String>,
    busy: Boolean,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(initialFeatures) { mutableStateOf(initialFeatures) }

    AdminSheetContainer(title = title, busy = busy, onDismiss = onDismiss) {
        Text(
            text = description,
            fontSize = 15.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFeatures.ALL.forEach { feature ->
                val isChecked = feature.key in selected
                FeatureCheckCard(
                    label = feature.label,
                    checked = isChecked,
                    enabled = !busy,
                    onCheckedChange = { checked ->
                        selected = if (checked) {
                            AppFeatures.ALL_KEYS.filter { it == feature.key || it in selected }
                        } else {
                            selected - feature.key
                        }
                    },
                )
            }
        }
        if (selected.isEmpty()) {
            Text(
                text = "Bác chọn ít nhất một quyền nhé.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NfcColors.AmberText,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .background(NfcColors.AmberBg, RoundedCornerShape(12.dp))
                    .padding(10.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineActionButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Để sau", fontWeight = FontWeight.ExtraBold)
            }
            SilverPrimaryButton(
                onClick = { onSave(selected) },
                enabled = !busy && selected.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (busy) "Đang lưu" else "Lưu", fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
            }
        }
    }
}

@Composable
private fun FeatureCheckCard(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val bg = if (checked) Color(0xFFE3F0FF) else Color.White
    val border = if (checked) Color(0xFF8FBCF0) else Color(0xFFC9DBEE)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null)
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = NfcColors.TextDeep,
        )
    }
}

@Composable
fun CreateBatchSheet(
    busy: Boolean,
    onCreate: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var quantityText by remember { mutableStateOf("1") }
    val presets = listOf("10", "50", "100", "500")

    AdminSheetContainer(title = "Tạo thẻ mới", busy = busy, onDismiss = onDismiss) {
        Text(
            text = "Nhập số thẻ cần tạo. Mỗi thẻ có một đường dẫn riêng để ghi vào thẻ hoặc in mã QR.",
            fontSize = 15.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Số thẻ cần tạo", fontSize = 14.sp, fontWeight = FontWeight.Black, color = NfcColors.TextDeep)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NfcColors.SurfaceMuted)
                    .clickable(enabled = !busy) {
                        val cur = quantityText.toIntOrNull() ?: 1
                        quantityText = maxOf(1, cur - 1).toString()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("−", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = NfcColors.TextDeep)
            }
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter { ch -> ch.isDigit() }.take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NfcColors.SurfaceMuted)
                    .clickable(enabled = !busy) {
                        val cur = quantityText.toIntOrNull() ?: 0
                        quantityText = minOf(500, cur + 1).toString()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = NfcColors.TextDeep)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                val isSelected = quantityText == preset
                if (isSelected) {
                    SilverPrimaryButton(
                        onClick = { quantityText = preset },
                        minHeightDp = 46,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(preset, fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
                    }
                } else {
                    LineActionButton(
                        onClick = { quantityText = preset },
                        minHeightDp = 46,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(preset, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        Text(
            text = "Tạo từ 1 đến 500 thẻ một lần.",
            fontSize = 13.5.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineActionButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Để sau", fontWeight = FontWeight.ExtraBold)
            }
            SilverPrimaryButton(
                onClick = {
                    val qty = quantityText.toIntOrNull() ?: 0
                    onCreate(qty)
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Tạo", fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
            }
        }
    }
}

@Composable
fun SingleInputActionSheet(
    title: String,
    description: String,
    warningBox: String? = null,
    fieldLabel: String,
    placeholder: String,
    helperText: String? = null,
    submitLabel: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("") }

    AdminSheetContainer(title = title, busy = busy, onDismiss = onDismiss) {
        Text(
            text = description,
            fontSize = 15.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!warningBox.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NfcColors.AmberBg, RoundedCornerShape(14.dp))
                    .border(1.dp, NfcColors.AmberBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text(warningBox, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NfcColors.AmberText)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(fieldLabel) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                fontSize = 13.5.sp,
                color = NfcColors.TextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineActionButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Để sau", fontWeight = FontWeight.ExtraBold)
            }
            SilverPrimaryButton(
                onClick = { onSubmit(value) },
                enabled = !busy && value.trim().isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (busy) "Đang xử lý" else submitLabel,
                    fontWeight = FontWeight.Black,
                    color = NfcColors.SilverBtnText,
                )
            }
        }
    }
}

@Composable
fun SeparateAccountConfirmSheet(
    tag: AdminNfcTag,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdminSheetContainer(title = "Tách ${tag.codeLabel} sang tài khoản riêng?", busy = busy, onDismiss = onDismiss) {
        Text(
            text = "Dùng khi khách lỡ nhập 2 thẻ vào cùng 1 máy hoặc muốn đưa thẻ này sang máy khác dùng độc lập.",
            fontSize = 15.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF0F9FF), RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            Text(
                text = "Mã kích hoạt và đường dẫn thẻ được giữ nguyên. Sau khi tách, khách chỉ cần nhập lại mã này trên máy thứ 2 để dùng tài khoản riêng mà không bị đá máy thứ 1 ra.",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0C4A6E),
            )
        }
        if (tag.machineId != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NfcColors.AmberBg, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "Thẻ này đang gắn máy trên tài khoản cũ nên sẽ được gỡ khỏi máy đó để sang tài khoản mới (dữ liệu máy cũ không bị xóa).",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NfcColors.AmberText,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineActionButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Để sau", fontWeight = FontWeight.ExtraBold)
            }
            SilverPrimaryButton(onClick = onConfirm, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Tách tài khoản", fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
            }
        }
    }
}

@Composable
fun DisableTagConfirmSheet(
    tag: AdminNfcTag,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdminSheetContainer(title = "Tắt ${tag.codeLabel}?", busy = busy, onDismiss = onDismiss) {
        Text(
            text = "Thẻ tắt rồi thì khách không dùng được nữa. Bác vẫn bật lại được sau.",
            fontSize = 15.5.sp,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineActionButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Không tắt", fontWeight = FontWeight.ExtraBold)
            }
            DangerActionButton(onClick = onConfirm, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("Tắt", fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            }
        }
    }
}
