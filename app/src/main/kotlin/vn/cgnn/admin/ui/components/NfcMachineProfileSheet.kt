package vn.cgnn.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import vn.cgnn.admin.data.model.AdminNfcTag
import vn.cgnn.admin.data.model.MachineBrand
import vn.cgnn.admin.data.model.MachineModel
import vn.cgnn.admin.data.model.MachineOption
import vn.cgnn.admin.data.model.MachineOwner
import vn.cgnn.admin.data.model.MachineProfile
import vn.cgnn.admin.data.model.OrderRecipientInfo
import vn.cgnn.admin.data.network.AdminApiClient
import vn.cgnn.admin.ui.theme.LineActionButton
import vn.cgnn.admin.ui.theme.NfcColors
import vn.cgnn.admin.ui.theme.SoftActionButton

private data class SeasonDraft(
    val id: Long? = null,
    val name: String = "",
    val activeMonths: Set<Int> = emptySet(),
    val hoursPerMonth: String = "",
    val note: String = "",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NfcMachineProfileSheet(
    tag: AdminNfcTag,
    api: AdminApiClient,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    var profile by remember { mutableStateOf<MachineProfile?>(null) }
    var owner by remember { mutableStateOf<MachineOwner?>(null) }
    var selectedCustomer by remember { mutableStateOf<MachineOwner?>(null) }
    var orderRecipient by remember { mutableStateOf<OrderRecipientInfo?>(null) }

    var customerQuery by remember { mutableStateOf("") }
    var customerResults by remember { mutableStateOf<List<MachineOwner>>(emptyList()) }
    var customersLoading by remember { mutableStateOf(false) }

    var brands by remember { mutableStateOf<List<MachineBrand>>(emptyList()) }
    var models by remember { mutableStateOf<List<MachineModel>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var usageOptions by remember { mutableStateOf<List<MachineOption>>(emptyList()) }
    var conditionOptions by remember { mutableStateOf<List<MachineOption>>(emptyList()) }

    // Form fields
    var brandId by remember { mutableStateOf<Long?>(null) }
    var modelId by remember { mutableStateOf<Long?>(null) }
    var machineTypeOptionId by remember { mutableStateOf<Long?>(null) }
    var nickname by remember { mutableStateOf("") }
    var yearOfPurchase by remember { mutableStateOf("") }
    var mainUsage by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentCondition by remember { mutableStateOf("") }
    var currentWorkingHours by remember { mutableStateOf("") }
    var hoursNote by remember { mutableStateOf("") }
    var currentOdometerKm by remember { mutableStateOf("") }
    var lastOilChangeDate by remember { mutableStateOf("") }
    var lastOilChangeHours by remember { mutableStateOf("") }
    var lastMaintenanceDate by remember { mutableStateOf("") }
    var lastMaintenanceHours by remember { mutableStateOf("") }
    var typicalDaysPerWeek by remember { mutableStateOf("") }
    var typicalHoursPerDay by remember { mutableStateOf("") }
    var offSeasonHoursPerMonth by remember { mutableStateOf("") }
    var registrationPlate by remember { mutableStateOf("") }
    var chassisNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var seasons by remember { mutableStateOf<List<SeasonDraft>>(emptyList()) }

    // Initial load
    LaunchedEffect(tag.id) {
        loading = true
        loadError = ""
        error = ""
        try {
            coroutineScope {
                val profDef = async { api.getNfcTagMachineProfile(tag.id) }
                val brandsDef = async { api.listMachineBrands() }
                val usageDef = async { api.listMachineUsageOptions() }
                val condDef = async { api.listMachineConditionOptions() }

                val profRes = profDef.await()
                brands = brandsDef.await()
                usageOptions = usageDef.await()
                conditionOptions = condDef.await()

                val p = profRes.profile
                profile = p
                owner = profRes.owner
                selectedCustomer = profRes.owner
                orderRecipient = profRes.orderRecipient

                if (p != null) {
                    brandId = p.brandId
                    modelId = p.modelId
                    machineTypeOptionId = p.machineTypeOptionId
                    nickname = p.nickname.orEmpty()
                    yearOfPurchase = p.yearOfPurchase?.toString().orEmpty()
                    mainUsage = p.mainUsage.orEmpty()
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                    currentCondition = p.currentCondition.orEmpty()
                    currentWorkingHours = (p.currentEngineHours ?: p.currentWorkingHours)?.let { formatNum(it) }.orEmpty()
                    currentOdometerKm = p.currentOdometerKm?.let { formatNum(it) }.orEmpty()
                    lastOilChangeDate = p.lastOilChangeDate.orEmpty()
                    lastOilChangeHours = p.lastOilChangeWorkingHours?.let { formatNum(it) }.orEmpty()
                    lastMaintenanceDate = p.lastMaintenanceDate.orEmpty()
                    lastMaintenanceHours = p.lastMaintenanceWorkingHours?.let { formatNum(it) }.orEmpty()
                    typicalDaysPerWeek = p.typicalWorkDaysPerWeek?.toString().orEmpty()
                    typicalHoursPerDay = p.typicalWorkHoursPerDay?.let { formatNum(it) }.orEmpty()
                    offSeasonHoursPerMonth = p.offSeasonHoursPerMonth?.let { formatNum(it) }.orEmpty()
                    registrationPlate = p.registrationPlate.orEmpty()
                    chassisNumber = p.chassisNumber.orEmpty()
                    notes = p.notes.orEmpty()
                    seasons = p.usageSeasons.map { s ->
                        SeasonDraft(
                            id = s.id,
                            name = s.seasonName.orEmpty(),
                            activeMonths = s.activeMonths.filter { it in 1..12 }.toSet(),
                            hoursPerMonth = s.hoursPerActiveMonth?.let { formatNum(it) }.orEmpty(),
                            note = s.note.orEmpty(),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            loadError = e.message ?: "Không tải được thông tin máy gắn với thẻ."
        } finally {
            loading = false
        }
    }

    // Load models when brandId changes
    LaunchedEffect(brandId) {
        val bid = brandId
        if (bid == null) {
            models = emptyList()
            return@LaunchedEffect
        }
        modelsLoading = true
        try {
            models = api.listMachineModels(bid)
        } catch (e: Exception) {
            error = e.message ?: "Không tải được danh sách dòng máy."
        } finally {
            modelsLoading = false
        }
    }

    // Search customers when unattached
    LaunchedEffect(customerQuery, profile, owner) {
        val q = customerQuery.trim()
        if (profile != null || owner != null || q.length < 2) {
            customerResults = emptyList()
            customersLoading = false
            return@LaunchedEffect
        }
        customersLoading = true
        delay(250L)
        try {
            customerResults = api.searchNfcMachineProfileCustomers(q)
        } catch (e: Exception) {
            error = e.message ?: "Không tìm được khách hàng."
        } finally {
            customersLoading = false
        }
    }

    val selectedModel = remember(modelId, models) { models.firstOrNull { it.id == modelId } }
    val capabilities = selectedModel?.capabilities ?: profile?.capabilities.orEmpty()
    val supportsHours = "operating_hours" in capabilities
    val supportsOdometer = "odometer" in capabilities
    val supportsSeasons = "seasonal_usage" in capabilities
    val supportsWorkSchedule = "work_usage_schedule" in capabilities

    AdminSheetContainer(
        title = "Cập nhật thông tin máy",
        busy = saving,
        onDismiss = onDismiss,
    ) {
        Text(
            text = "${tag.codeLabel} · ${if (profile != null) tag.machineLabel ?: "Máy của khách" else "Chưa gắn máy"}",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = NfcColors.TextMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        when {
            loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                    Text("Đang tải thông tin máy...", fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
                }
            }

            loadError.isNotBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NfcColors.DangerBg, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(loadError, color = NfcColors.DangerText, fontWeight = FontWeight.Bold)
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Owner section when creating a new machine
                    if (profile == null) {
                        ProfileSection("Máy này dành cho khách nào?") {
                            val currentOwner = owner
                            if (currentOwner != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NfcColors.EmeraldBg, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = "Thẻ đã được khách nhận. Máy sẽ gắn vào tài khoản ${currentOwner.name.ifBlank { "Khách hàng" }}${if (currentOwner.phone.isNotBlank()) " · ${currentOwner.phone}" else ""}.",
                                        color = NfcColors.EmeraldText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                            } else {
                                val rec = listOfNotNull(orderRecipient?.name, orderRecipient?.phone)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                if (rec.isNotEmpty()) {
                                    Text("Người nhận trên đơn: $rec", fontSize = 14.sp, color = NfcColors.TextMuted)
                                }
                                OutlinedTextField(
                                    value = customerQuery,
                                    onValueChange = { customerQuery = it.take(120) },
                                    label = { Text("Tìm tài khoản khách (tên hoặc SĐT)") },
                                    placeholder = { Text("Gõ ít nhất 2 ký tự") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (selectedCustomer != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(NfcColors.EmeraldBg, RoundedCornerShape(12.dp))
                                            .border(1.dp, NfcColors.EmeraldBorder, RoundedCornerShape(12.dp))
                                            .padding(10.dp),
                                    ) {
                                        Text(
                                            text = "Khách đã chọn: ${selectedCustomer!!.name.ifBlank { "Khách hàng" }} · ${selectedCustomer!!.phone}",
                                            color = NfcColors.EmeraldText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                                if (customerQuery.trim().length >= 2) {
                                    if (customersLoading) {
                                        Text("Đang tìm khách...", fontSize = 13.5.sp, color = NfcColors.TextMuted)
                                    } else if (customerResults.isEmpty()) {
                                        Text("Không tìm thấy tài khoản khách phù hợp.", fontSize = 13.5.sp, color = NfcColors.TextMuted)
                                    } else {
                                        customerResults.forEach { customer ->
                                            val isSel = selectedCustomer?.id == customer.id
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSel) NfcColors.EmeraldBg else Color.White)
                                                    .border(
                                                        1.dp,
                                                        if (isSel) NfcColors.EmeraldText else NfcColors.CardDivider,
                                                        RoundedCornerShape(12.dp),
                                                    )
                                                    .clickable { selectedCustomer = customer }
                                                    .padding(10.dp),
                                            ) {
                                                Column {
                                                    Text(customer.name.ifBlank { "Khách hàng" }, fontWeight = FontWeight.Bold, color = NfcColors.TextDeep)
                                                    Text(customer.phone.ifBlank { "Chưa có số điện thoại" }, fontSize = 12.5.sp, color = NfcColors.TextMuted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Brand & Model section
                    ProfileSection("Hãng và dòng máy") {
                        PickerDropdown(
                            label = "Hãng máy",
                            selectedText = brands.firstOrNull { it.id == brandId }?.name ?: "Chọn hãng máy",
                            items = brands.map { it.id to it.name },
                            onSelect = { id ->
                                brandId = id
                                modelId = null
                                machineTypeOptionId = null
                            },
                        )
                        PickerDropdown(
                            label = "Dòng máy",
                            selectedText = when {
                                modelsLoading -> "Đang tải dòng máy..."
                                else -> models.firstOrNull { it.id == modelId }?.name ?: "Chọn dòng máy"
                            },
                            enabled = brandId != null && !modelsLoading,
                            items = models.map { it.id to it.name },
                            onSelect = { id ->
                                modelId = id
                                machineTypeOptionId = models.firstOrNull { it.id == id }?.machineTypeOptionId
                            },
                        )
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it.take(120) },
                            label = { Text("Tên gọi riêng nếu có") },
                            placeholder = { Text("Ví dụ: Máy cày nhà mình") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = yearOfPurchase,
                            onValueChange = { yearOfPurchase = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("Năm mua máy") },
                            placeholder = { Text("Ví dụ: 2020") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Condition & Main Usage
                    ProfileSection("Tình trạng và việc máy thường làm") {
                        val condItems = buildList {
                            add("" to "Chọn tình trạng máy")
                            if (currentCondition.isNotBlank() && conditionOptions.none { it.label == currentCondition }) {
                                add(currentCondition to currentCondition)
                            }
                            conditionOptions.forEach { add(it.label to it.label) }
                        }
                        StringPickerDropdown(
                            label = "Tình trạng máy",
                            selectedText = currentCondition.ifBlank { "Chọn tình trạng máy" },
                            items = condItems,
                            onSelect = { currentCondition = it },
                        )

                        if (usageOptions.isNotEmpty()) {
                            Text("Máy thường làm việc gì?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextDeep)
                            usageOptions.forEach { opt ->
                                val key = opt.key?.takeIf { it.isNotBlank() } ?: opt.label
                                val checked = key in mainUsage
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, NfcColors.CardDivider, RoundedCornerShape(12.dp))
                                        .clickable {
                                            mainUsage = if (checked) mainUsage - key else mainUsage + key
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { c -> mainUsage = if (c) mainUsage + key else mainUsage - key },
                                    )
                                    Text(opt.label.ifBlank { key }, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = registrationPlate,
                            onValueChange = { registrationPlate = it.take(24) },
                            label = { Text("Biển số nếu có") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = chassisNumber,
                            onValueChange = { chassisNumber = it.take(80) },
                            label = { Text("Số khung nếu có") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it.take(5000) },
                            label = { Text("Vấn đề hoặc ghi chú về máy") },
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Hours, Odometer & Maintenance
                    ProfileSection("Giờ máy, số km và bảo dưỡng") {
                        if (supportsHours || supportsOdometer) {
                            if (supportsHours) {
                                DecimalField("Giờ máy hiện tại", currentWorkingHours, "Ví dụ: 1250") { currentWorkingHours = it }
                                OutlinedTextField(
                                    value = hoursNote,
                                    onValueChange = { hoursNote = it.take(5000) },
                                    label = { Text("Ghi chú khi sửa giờ máy") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (supportsOdometer) {
                                DecimalField("Số km hiện tại", currentOdometerKm, "Ví dụ: 125000") { currentOdometerKm = it }
                            }
                            OutlinedTextField(
                                value = lastOilChangeDate,
                                onValueChange = { lastOilChangeDate = it.take(10) },
                                label = { Text("Ngày thay dầu gần nhất (YYYY-MM-DD)") },
                                placeholder = { Text("2026-05-15") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (supportsHours) {
                                DecimalField("Giờ máy lúc thay dầu", lastOilChangeHours, "") { lastOilChangeHours = it }
                            }
                            OutlinedTextField(
                                value = lastMaintenanceDate,
                                onValueChange = { lastMaintenanceDate = it.take(10) },
                                label = { Text("Ngày bảo dưỡng gần nhất (YYYY-MM-DD)") },
                                placeholder = { Text("2026-05-15") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (supportsHours) {
                                DecimalField("Giờ máy lúc bảo dưỡng", lastMaintenanceHours, "") { lastMaintenanceHours = it }
                            }
                        } else {
                            Text(
                                text = "Dòng máy này chưa dùng số giờ máy. Bác có thể cập nhật hãng, dòng máy, tình trạng và ghi chú ở phía trên.",
                                fontSize = 14.sp,
                                color = NfcColors.TextMuted,
                            )
                        }
                    }

                    // Seasonal usage
                    if (supportsSeasons) {
                        ProfileSection("Lịch vụ mùa") {
                            seasons.forEachIndexed { idx, season ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .border(1.dp, NfcColors.CardDivider, RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Vụ ${idx + 1}", fontWeight = FontWeight.Black, color = NfcColors.TextDeep)
                                        TextButton(onClick = { seasons = seasons.filterIndexed { i, _ -> i != idx } }) {
                                            Text("Xóa", color = NfcColors.DangerText, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    OutlinedTextField(
                                        value = season.name,
                                        onValueChange = { v ->
                                            seasons = seasons.mapIndexed { i, s -> if (i == idx) s.copy(name = v) else s }
                                        },
                                        label = { Text("Tên vụ (ví dụ: Vụ Đông Xuân)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text("Tháng chạy máy:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (m in 1..12) {
                                            val sel = m in season.activeMonths
                                            FilterChip(
                                                selected = sel,
                                                onClick = {
                                                    val nextMonths = if (sel) season.activeMonths - m else season.activeMonths + m
                                                    seasons = seasons.mapIndexed { i, s -> if (i == idx) s.copy(activeMonths = nextMonths) else s }
                                                },
                                                label = { Text("T$m") },
                                            )
                                        }
                                    }
                                    DecimalField("Số giờ mỗi tháng trong vụ", season.hoursPerMonth, "Ví dụ: 80") { v ->
                                        seasons = seasons.mapIndexed { i, s -> if (i == idx) s.copy(hoursPerMonth = v) else s }
                                    }
                                }
                            }
                            LineActionButton(
                                onClick = { if (seasons.size < 12) seasons = seasons + SeasonDraft() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("+ Thêm vụ mùa", fontWeight = FontWeight.ExtraBold)
                            }
                            DecimalField("Giờ máy mỗi tháng ngoài vụ", offSeasonHoursPerMonth, "Có thể để trống") {
                                offSeasonHoursPerMonth = it
                            }
                        }
                    }

                    // Work schedule
                    if (supportsWorkSchedule) {
                        ProfileSection("Nhịp làm việc thường ngày") {
                            OutlinedTextField(
                                value = typicalDaysPerWeek,
                                onValueChange = { typicalDaysPerWeek = it.filter { c -> c.isDigit() }.take(1) },
                                label = { Text("Số ngày máy thường làm trong tuần (0-7)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DecimalField("Số giờ máy thường chạy trong ngày (0-24)", typicalHoursPerDay, "Ví dụ: 6") {
                                typicalHoursPerDay = it
                            }
                        }
                    }

                    if (error.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NfcColors.DangerBg, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(error, color = NfcColors.DangerText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LineActionButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) {
                            Text("Đóng", fontWeight = FontWeight.ExtraBold)
                        }
                        val machineOwner = owner ?: selectedCustomer
                        SoftActionButton(
                            onClick = {
                                error = ""
                                val bId = brandId
                                val mId = modelId
                                if (bId == null || mId == null) {
                                    error = "Bác chọn đúng hãng máy và dòng máy trong danh sách trước khi lưu."
                                    return@SoftActionButton
                                }
                                if (profile == null && machineOwner == null) {
                                    error = "Bác tìm và chọn tài khoản của khách sẽ dùng máy này."
                                    return@SoftActionButton
                                }
                                val daysInt = typicalDaysPerWeek.trim().toIntOrNull()
                                val hoursDay = typicalHoursPerDay.trim().toDoubleOrNull()
                                if (supportsWorkSchedule && ((daysInt == null) != (hoursDay == null))) {
                                    error = "Bác nhập đủ số ngày và số giờ máy thường làm trong tuần nhé."
                                    return@SoftActionButton
                                }

                                val payload = buildJsonObject {
                                    put("brand_id", bId)
                                    put("model_id", mId)
                                    val mtId = selectedModel?.machineTypeOptionId ?: machineTypeOptionId
                                    if (mtId != null) put("machine_type_option_id", mtId) else put("machine_type_option_id", JsonNull)
                                    putNullableString("nickname", nickname)
                                    putNullableInt("year_of_purchase", yearOfPurchase)
                                    put("main_usage", mainUsage.joinToString(","))
                                    putNullableString("current_condition", currentCondition)
                                    putNullableString("last_oil_change_date", lastOilChangeDate)
                                    putNullableString("last_maintenance_date", lastMaintenanceDate)
                                    putNullableString("registration_plate", registrationPlate)
                                    putNullableString("chassis_number", chassisNumber)
                                    putNullableString("notes", notes)

                                    if (profile == null && machineOwner != null) {
                                        put("owner_user_id", machineOwner.id)
                                    }
                                    if (supportsHours) {
                                        putNullableDouble("last_oil_change_working_hours", lastOilChangeHours)
                                        putNullableDouble("last_maintenance_working_hours", lastMaintenanceHours)
                                        val curHrs = currentWorkingHours.trim().toDoubleOrNull()
                                        if (curHrs != null) {
                                            put("current_working_hours", curHrs)
                                            put("hours_note", hoursNote.trim().ifEmpty { "Quản trị viên cập nhật hồ sơ máy." })
                                        }
                                        put("engine_hours_estimation_enabled", profile?.engineHoursEstimationEnabled == true)
                                    }
                                    if (supportsOdometer) {
                                        putNullableDouble("current_odometer_km", currentOdometerKm)
                                    }
                                    if (supportsSeasons) {
                                        val validSeasons = seasons.filter { it.activeMonths.isNotEmpty() }
                                        put("usage_seasons", buildJsonArray {
                                            validSeasons.forEach { s ->
                                                add(buildJsonObject {
                                                    if (s.id != null) put("id", s.id)
                                                    putNullableString("season_name", s.name)
                                                    put("active_months", buildJsonArray {
                                                        s.activeMonths.sorted().forEach { add(JsonPrimitive(it)) }
                                                    })
                                                    putNullableDouble("hours_per_active_month", s.hoursPerMonth)
                                                    putNullableString("note", s.note)
                                                })
                                            }
                                        })
                                        putNullableDouble("off_season_hours_per_month", offSeasonHoursPerMonth)
                                    }
                                    if (supportsWorkSchedule) {
                                        if (daysInt != null) put("typical_work_days_per_week", daysInt) else put("typical_work_days_per_week", JsonNull)
                                        if (hoursDay != null) put("typical_work_hours_per_day", hoursDay) else put("typical_work_hours_per_day", JsonNull)
                                    }
                                }

                                scope.launch {
                                    saving = true
                                    try {
                                        api.updateNfcTagMachineProfile(tag.id, payload)
                                        onSaved()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        error = e.message ?: "Chưa lưu được thông tin máy. Bác kiểm tra lại giúp em."
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving && !loading && !modelsLoading && (profile != null || machineOwner != null),
                            modifier = Modifier.weight(1.3f),
                        ) {
                            Text(
                                text = when {
                                    saving -> "Đang lưu..."
                                    profile != null -> "Lưu thông tin máy"
                                    else -> "Tạo máy & gắn thẻ"
                                },
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NfcColors.SurfaceSoft, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Black, color = NfcColors.TextDeep)
        content()
    }
}

@Composable
private fun DecimalField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == '.' }
            onValueChange(filtered)
        },
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PickerDropdown(
    label: String,
    selectedText: String,
    items: List<Pair<Long, String>>,
    enabled: Boolean = true,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) Color.White else Color(0xFFEFF2F6))
                .border(1.dp, Color(0xFFC8CFD8), RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedText, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextDeep)
                Text("▾", fontSize = 16.sp, color = NfcColors.TextMuted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun StringPickerDropdown(
    label: String,
    selectedText: String,
    items: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFC8CFD8), RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selectedText, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextDeep)
                Text("▾", fontSize = 16.sp, color = NfcColors.TextMuted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

private fun formatNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(key: String, value: String) {
    val clean = value.trim()
    if (clean.isEmpty()) put(key, JsonNull) else put(key, clean)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(key: String, value: String) {
    val parsed = value.trim().toIntOrNull()
    if (parsed == null) put(key, JsonNull) else put(key, parsed)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableDouble(key: String, value: String) {
    val parsed = value.trim().toDoubleOrNull()
    if (parsed == null) put(key, JsonNull) else put(key, parsed)
}
