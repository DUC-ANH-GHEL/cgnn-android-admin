package vn.cgnn.admin.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PancakeActivationSync(
    val status: String? = null,
    val label: String? = null,
    val error: String? = null,
    @SerialName("synced_at") val syncedAt: String? = null,
    @SerialName("manual_required") val manualRequired: Boolean? = null,
)

@Serializable
data class LoginDeviceInfo(
    val label: String? = null,
    val os: String? = null,
    val browser: String? = null,
    @SerialName("logged_in_at") val loggedInAt: String? = null,
)

@Serializable
data class AdminNfcTag(
    val id: Long,
    @SerialName("display_code") val displayCode: String? = null,
    val status: String? = null,
    val features: List<String> = emptyList(),
    @SerialName("masked_token") val maskedToken: String? = null,
    @SerialName("token_masked") val tokenMasked: String? = null,
    @SerialName("machine_id") val machineId: Long? = null,
    @SerialName("machine_label") val machineLabel: String? = null,
    @SerialName("order_id") val orderId: Long? = null,
    @SerialName("order_code") val orderCode: String? = null,
    @SerialName("order_recipient_name") val orderRecipientName: String? = null,
    @SerialName("order_recipient_phone") val orderRecipientPhone: String? = null,
    @SerialName("order_status") val orderStatus: String? = null,
    @SerialName("order_status_label") val orderStatusLabel: String? = null,
    @SerialName("order_tag_position") val orderTagPosition: Int? = null,
    @SerialName("order_tag_total") val orderTagTotal: Int? = null,
    @SerialName("has_activation_code") val hasActivationCode: Boolean = false,
    @SerialName("activation_started_at") val activationStartedAt: String? = null,
    @SerialName("activation_phone") val activationPhone: String? = null,
    @SerialName("activation_device_brand") val activationDeviceBrand: String? = null,
    @SerialName("activation_device_model") val activationDeviceModel: String? = null,
    @SerialName("activation_device_label") val activationDeviceLabel: String? = null,
    @SerialName("activation_device_os") val activationDeviceOs: String? = null,
    @SerialName("activation_device_browser") val activationDeviceBrowser: String? = null,
    @SerialName("activation_user_agent") val activationUserAgent: String? = null,
    @SerialName("activated_at") val activatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val url: String? = null,
    @SerialName("latest_login_device") val latestLoginDevice: LoginDeviceInfo? = null,
    @SerialName("pancake_activation_sync") val pancakeActivationSync: PancakeActivationSync? = null,
) {
    val codeLabel: String
        get() = displayCode?.takeIf { it.isNotBlank() } ?: "Thẻ #$id"

    val effectivePhone: String
        get() = orderRecipientPhone?.takeIf { it.isNotBlank() }
            ?: activationPhone?.takeIf { it.isNotBlank() }
            ?: ""

    val canProgram: Boolean
        get() = status == "unactivated" || status == "active"

    val isOff: Boolean
        get() = status == "revoked" || status == "replaced"

    val hasCode: Boolean
        get() = hasActivationCode || effectivePhone.isNotBlank()

    val needsAttention: Boolean
        get() = !activationStartedAt.isNullOrBlank() || !pancakeActivationSync?.error.isNullOrBlank()

    val canEditMachine: Boolean
        get() = (status == "active" || status == "unactivated") && features.contains("machine_care")
}

@Serializable
data class FullAccessCodeRow(
    @SerialName("display_code") val displayCode: String = "",
    @SerialName("activation_code") val activationCode: String = "",
    val link: String = "",
    val features: List<String> = emptyList(),
)

@Serializable
data class PancakeSyncAllResult(
    val processed: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    @SerialName("manual_required") val manualRequired: Int = 0,
    val remaining: Int = 0,
    @SerialName("retry_failed_before") val retryFailedBefore: String? = null,
)

@Serializable
data class NfcGuideContent(
    val title: String = DEFAULT_TITLE,
    val subtitle: String = DEFAULT_SUBTITLE,
    @SerialName("for_label") val forLabel: String = DEFAULT_FOR_LABEL,
    val tagline: List<String> = DEFAULT_TAGLINE,
    @SerialName("step1_title") val step1Title: String = DEFAULT_STEP1_TITLE,
    @SerialName("step1_text") val step1Text: String = DEFAULT_STEP1_TEXT,
    @SerialName("step2_title") val step2Title: String = DEFAULT_STEP2_TITLE,
    @SerialName("step2_text") val step2Text: String = DEFAULT_STEP2_TEXT,
    @SerialName("step3_title") val step3Title: String = DEFAULT_STEP3_TITLE,
    @SerialName("step3_text") val step3Text: String = DEFAULT_STEP3_TEXT,
    val phone: String = DEFAULT_PHONE,
    val foot1: String = DEFAULT_FOOT1,
    val foot2: String = DEFAULT_FOOT2,
    val wish: String = DEFAULT_WISH,
) {
    companion object {
        const val MAX_CARDS = 40
        const val DEFAULT_TITLE = "THẺ ĐO ĐẤT"
        const val DEFAULT_SUBTITLE = "HƯỚNG DẪN SỬ DỤNG"
        const val DEFAULT_FOR_LABEL = "Dành cho bác"
        val DEFAULT_TAGLINE = listOf("Đơn giản", "Nhanh chóng", "Dễ sử dụng")
        const val DEFAULT_STEP1_TITLE = "MỞ ĐIỆN THOẠI LÊN QUÉT MÃ QR"
        const val DEFAULT_STEP1_TEXT = "Mở điện thoại lên để quét mã QR bên cạnh. Bác có thể quét bằng máy ảnh, Zalo,..."
        const val DEFAULT_STEP2_TITLE = "NHẬP MÃ KÍCH HOẠT"
        const val DEFAULT_STEP2_TEXT = "Khi có yêu cầu nhập mã kích hoạt, bác nhập mã kích hoạt này vào nhé"
        const val DEFAULT_STEP3_TITLE = "GỌI ĐIỆN CHO SHOP"
        const val DEFAULT_STEP3_TEXT = "Nếu có thắc mắc, bác cứ gọi điện cho shop nhé"
        const val DEFAULT_PHONE = "0942 18 02 36"
        const val DEFAULT_FOOT1 = "Mọi dòng điện thoại đều dùng được."
        const val DEFAULT_FOOT2 = "An toàn – Bảo mật thông tin máy của bạn."
        const val DEFAULT_WISH = "Chúc các bác làm việc với máy hiệu quả hơn mỗi ngày."

        val DEFAULT = NfcGuideContent()
    }
}

data class NfcGuideCardData(
    val tagId: Long,
    val name: String,
    val code: String,
    val link: String,
)

@Serializable
data class MachineOwner(
    val id: Long,
    val name: String = "",
    val phone: String = "",
)

@Serializable
data class OrderRecipientInfo(
    val name: String? = null,
    val phone: String? = null,
)

@Serializable
data class UsageSeason(
    val id: Long? = null,
    @SerialName("season_name") val seasonName: String? = null,
    @SerialName("active_months") val activeMonths: List<Int> = emptyList(),
    @SerialName("hours_per_active_month") val hoursPerActiveMonth: Double? = null,
    val note: String? = null,
    @SerialName("usage_input") val usageInput: JsonElement? = null,
)

@Serializable
data class MachineProfile(
    val id: Long? = null,
    @SerialName("brand_id") val brandId: Long? = null,
    @SerialName("brand_label") val brandLabel: String? = null,
    @SerialName("model_id") val modelId: Long? = null,
    @SerialName("model_label") val modelLabel: String? = null,
    @SerialName("machine_type_option_id") val machineTypeOptionId: Long? = null,
    @SerialName("machine_type_key") val machineTypeKey: String? = null,
    val capabilities: List<String> = emptyList(),
    val nickname: String? = null,
    @SerialName("year_of_purchase") val yearOfPurchase: Int? = null,
    @SerialName("main_usage") val mainUsage: String? = null,
    @SerialName("current_condition") val currentCondition: String? = null,
    @SerialName("current_engine_hours") val currentEngineHours: Double? = null,
    @SerialName("current_working_hours") val currentWorkingHours: Double? = null,
    @SerialName("current_odometer_km") val currentOdometerKm: Double? = null,
    @SerialName("engine_hours_estimation_enabled") val engineHoursEstimationEnabled: Boolean = false,
    @SerialName("last_oil_change_date") val lastOilChangeDate: String? = null,
    @SerialName("last_oil_change_working_hours") val lastOilChangeWorkingHours: Double? = null,
    @SerialName("last_maintenance_date") val lastMaintenanceDate: String? = null,
    @SerialName("last_maintenance_working_hours") val lastMaintenanceWorkingHours: Double? = null,
    @SerialName("typical_work_days_per_week") val typicalWorkDaysPerWeek: Int? = null,
    @SerialName("typical_work_hours_per_day") val typicalWorkHoursPerDay: Double? = null,
    @SerialName("off_season_hours_per_month") val offSeasonHoursPerMonth: Double? = null,
    @SerialName("registration_plate") val registrationPlate: String? = null,
    @SerialName("chassis_number") val chassisNumber: String? = null,
    val notes: String? = null,
    @SerialName("usage_seasons") val usageSeasons: List<UsageSeason> = emptyList(),
)

@Serializable
data class MachineProfileResponse(
    val profile: MachineProfile? = null,
    val owner: MachineOwner? = null,
    @SerialName("order_recipient") val orderRecipient: OrderRecipientInfo? = null,
)

@Serializable
data class MachineBrand(
    val id: Long,
    val name: String = "",
)

@Serializable
data class MachineModel(
    val id: Long,
    val name: String = "",
    @SerialName("brand_id") val brandId: Long? = null,
    @SerialName("machine_type_option_id") val machineTypeOptionId: Long? = null,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class MachineOption(
    val id: Long,
    val key: String? = null,
    val label: String = "",
)

data class AppFeatureDef(
    val key: String,
    val label: String,
)

object AppFeatures {
    val ALL = listOf(
        AppFeatureDef("machine_care", "Máy của bác"),
        AppFeatureDef("land_measure", "Đo đất"),
        AppFeatureDef("work_ledger", "Sổ công việc"),
    )
    val ALL_KEYS = ALL.map { it.key }
    val LABEL_BY_KEY = ALL.associate { it.key to it.label }
}

data class PancakeOrderStatusDef(
    val code: String,
    val label: String,
    val badgeKey: String,
)

object PancakeOrderStatuses {
    val ALL = listOf(
        PancakeOrderStatusDef("0", "Mới", "pending"),
        PancakeOrderStatusDef("10", "Cần xử lý", "pending"),
        PancakeOrderStatusDef("17", "Chờ xác nhận", "pending"),
        PancakeOrderStatusDef("11", "Chờ hàng", "pending"),
        PancakeOrderStatusDef("12", "Chờ in", "processing"),
        PancakeOrderStatusDef("13", "Đã in", "processing"),
        PancakeOrderStatusDef("14", "Đang đổi", "processing"),
        PancakeOrderStatusDef("20", "Đã đặt hàng", "processing"),
        PancakeOrderStatusDef("1", "Đã xác nhận", "processing"),
        PancakeOrderStatusDef("8", "Đang đóng hàng", "processing"),
        PancakeOrderStatusDef("9", "Chờ chuyển hàng", "processing"),
        PancakeOrderStatusDef("2", "Đã gửi hàng", "shipped"),
        PancakeOrderStatusDef("3", "Đã nhận", "delivered"),
        PancakeOrderStatusDef("16", "Đã thu tiền", "paid"),
        PancakeOrderStatusDef("4", "Đang hoàn", "cancelled"),
        PancakeOrderStatusDef("15", "Hoàn một phần", "cancelled"),
        PancakeOrderStatusDef("5", "Đã hoàn", "cancelled"),
        PancakeOrderStatusDef("6", "Đã hủy", "cancelled"),
        PancakeOrderStatusDef("7", "Đã xóa", "cancelled"),
        PancakeOrderStatusDef("-1", "Đã hủy", "cancelled"),
    )

    val BY_CODE = ALL.associateBy { it.code }
}

object TagStatusLabels {
    val FULL = mapOf(
        "unactivated" to "Dùng được, chưa gắn máy",
        "active" to "Dùng được, đã gắn máy",
        "revoked" to "Đã tắt",
        "replaced" to "Đã thay",
    )

    val FILTER = listOf(
        "" to "Tất cả",
        "unactivated" to "Chưa gắn máy",
        "active" to "Đã gắn máy",
        "revoked" to "Đã tắt",
        "replaced" to "Đã thay",
    )

    fun cardLabel(status: String?): String = when (status) {
        "unactivated" -> "Chưa gắn máy"
        "active" -> "Đã gắn máy"
        "revoked" -> "Đã tắt"
        "replaced" -> "Đã thay bằng thẻ khác"
        else -> status ?: "Chưa rõ"
    }
}
