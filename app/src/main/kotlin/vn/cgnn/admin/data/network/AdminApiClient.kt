package vn.cgnn.admin.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import vn.cgnn.admin.BuildConfig
import vn.cgnn.admin.data.model.AdminNfcTag
import vn.cgnn.admin.data.model.FullAccessCodeRow
import vn.cgnn.admin.data.model.MachineBrand
import vn.cgnn.admin.data.model.MachineModel
import vn.cgnn.admin.data.model.MachineOption
import vn.cgnn.admin.data.model.MachineOwner
import vn.cgnn.admin.data.model.MachineProfileResponse
import vn.cgnn.admin.data.model.NfcGuideContent
import vn.cgnn.admin.data.model.PancakeActivationSync
import vn.cgnn.admin.data.model.PancakeSyncAllResult
import vn.cgnn.admin.data.storage.AdminPreferences
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(
    val statusCode: Int,
    override val message: String,
    val errorCode: String? = null,
) : Exception(message)

class AdminSessionExpiredException(message: String = "Phiên đăng nhập đã hết hạn. Bác vui lòng đăng nhập lại.") :
    Exception(message)

class AdminApiClient(
    private val prefs: AdminPreferences,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val refreshMutex = Mutex()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(prefs.cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var onSessionExpired: (() -> Unit)? = null

    private fun buildUrl(path: String, query: Map<String, String?> = emptyMap()): String {
        val full = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
        val builder = full.toHttpUrl().newBuilder()
        query.forEach { (key, value) ->
            if (!value.isNullOrBlank()) {
                builder.addQueryParameter(key, value)
            }
        }
        return builder.build().toString()
    }

    private fun extractErrorDetail(bodyText: String, statusCode: Int): Pair<String, String?> {
        if (bodyText.isBlank()) {
            return ("Lỗi kết nối máy chủ ($statusCode)." to null)
        }
        return runCatching {
            val root = json.parseToJsonElement(bodyText).jsonObject
            val errorCode = root["error_code"]?.jsonPrimitive?.contentOrNull
            val detailElem = root["detail"]
            val detail = when (detailElem) {
                is JsonPrimitive -> detailElem.contentOrNull
                is JsonObject -> detailElem["message"]?.jsonPrimitive?.contentOrNull
                    ?: detailElem.toString()
                else -> root["message"]?.jsonPrimitive?.contentOrNull
            }
            (detail?.takeIf { it.isNotBlank() } ?: "Lỗi máy chủ ($statusCode).") to errorCode
        }.getOrElse {
            "Lỗi máy chủ ($statusCode)." to null
        }
    }

    private suspend fun refreshAdminToken(): String = refreshMutex.withLock {
        if (prefs.isLoggedOut()) throw AdminSessionExpiredException()
        val retryKey = prefs.getOrCreateRefreshRetryKey()
        var attempt = 0
        while (true) {
            val bodyJson = buildJsonObject { put("retry_key", retryKey) }.toString()
            val request = Request.Builder()
                .url(buildUrl("/users/refresh"))
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()
            val (code, text) = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { resp ->
                    resp.code to (resp.body?.string().orEmpty())
                }
            }
            if (code in 200..299) {
                val root = json.parseToJsonElement(text).jsonObject
                val newToken = root["access_token"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (newToken.isBlank()) throw AdminSessionExpiredException()
                prefs.saveAccessToken(newToken)
                prefs.clearRefreshRetryKey()
                return@withLock newToken
            }
            val (msg, errCode) = extractErrorDetail(text, code)
            if (code == 409 && errCode in setOf("ADMIN_REFRESH_ROTATING", "ADMIN_REFRESH_STALE") && attempt < 2) {
                attempt += 1
                delay(250L * attempt)
                continue
            }
            if (code == 401 || code == 403) {
                prefs.clearAccessToken()
                onSessionExpired?.invoke()
                throw AdminSessionExpiredException(msg)
            }
            throw ApiException(code, msg, errCode)
        }
        @Suppress("UNREACHABLE_CODE")
        throw AdminSessionExpiredException()
    }

    private suspend fun executeRaw(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: RequestBody? = null,
        noCache: Boolean = false,
        attachAuth: Boolean = true,
        retriedAfter401: Boolean = false,
    ): ByteArray {
        val url = buildUrl(path, query)
        val reqBuilder = Request.Builder().url(url)
        if (noCache) {
            reqBuilder.header("Cache-Control", "no-cache")
            reqBuilder.header("Pragma", "no-cache")
        }
        val token = if (attachAuth) prefs.getAccessToken() else null
        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }
        when (method.uppercase()) {
            "GET" -> reqBuilder.get()
            "POST" -> reqBuilder.post(body ?: "{}".toRequestBody(jsonMediaType))
            "PUT" -> reqBuilder.put(body ?: "{}".toRequestBody(jsonMediaType))
            "PATCH" -> reqBuilder.patch(body ?: "{}".toRequestBody(jsonMediaType))
            "DELETE" -> reqBuilder.delete(body)
        }
        val request = reqBuilder.build()

        val (statusCode, bytes) = withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { resp ->
                    resp.code to (resp.body?.bytes() ?: ByteArray(0))
                }
            } catch (e: IOException) {
                throw ApiException(0, "Không kết nối được máy chủ. Bác kiểm tra lại mạng nhé.")
            }
        }

        if (statusCode == 401 && attachAuth && !retriedAfter401) {
            refreshAdminToken()
            return executeRaw(method, path, query, body, noCache, attachAuth = true, retriedAfter401 = true)
        }
        if (statusCode == 401 && attachAuth) {
            prefs.clearAccessToken()
            onSessionExpired?.invoke()
            throw AdminSessionExpiredException()
        }
        if (statusCode !in 200..299) {
            val (msg, errCode) = extractErrorDetail(String(bytes, Charsets.UTF_8), statusCode)
            throw ApiException(statusCode, msg, errCode)
        }
        return bytes
    }

    private suspend fun executeJson(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        jsonBody: JsonElement? = null,
        noCache: Boolean = false,
        attachAuth: Boolean = true,
    ): JsonElement {
        val body = jsonBody?.let { it.toString().toRequestBody(jsonMediaType) }
        val bytes = executeRaw(method, path, query, body, noCache, attachAuth)
        val text = String(bytes, Charsets.UTF_8).trim()
        if (text.isEmpty()) return JsonObject(emptyMap())
        return json.parseToJsonElement(text)
    }

    private fun unwrapData(root: JsonElement): JsonElement {
        val obj = root as? JsonObject ?: return root
        return obj["data"] ?: root
    }

    // ==================== AUTH ====================

    suspend fun login(email: String, password: String): String {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }
        val root = executeJson("POST", "/users/login", jsonBody = body, attachAuth = false)
        val token = root.jsonObject["access_token"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (token.isBlank()) throw ApiException(401, "Không nhận được phiên đăng nhập.")
        if (!AdminAuthUtils.isUsableAdminToken(token)) {
            throw ApiException(403, "Tài khoản này không có quyền quản trị Thẻ kích hoạt máy.")
        }
        prefs.saveAccessToken(token)
        prefs.saveEmail(email.trim())
        prefs.clearRefreshRetryKey()
        return token
    }

    suspend fun restoreSession(): Boolean {
        val existing = prefs.getAccessToken()
        if (AdminAuthUtils.isUsableAdminToken(existing)) return true
        if (prefs.isLoggedOut()) return false
        return runCatching {
            val fresh = refreshAdminToken()
            AdminAuthUtils.isUsableAdminToken(fresh)
        }.getOrDefault(false)
    }

    suspend fun logout() {
        prefs.setLoggedOut(true)
        prefs.clearAccessToken()
        prefs.clearRefreshRetryKey()
        runCatching {
            executeRaw("POST", "/users/logout", body = "{}".toRequestBody(jsonMediaType), attachAuth = false)
        }
        prefs.clearCookies()
    }

    // ==================== NFC TAGS ====================

    suspend fun listNfcTags(
        search: String = "",
        status: String = "",
        orderStatus: String = "",
        limit: Int = 100,
    ): List<AdminNfcTag> {
        val root = executeJson(
            method = "GET",
            path = "/admin/machine-care/nfc-tags",
            query = mapOf(
                "limit" to limit.toString(),
                "q" to search.trim().ifBlank { null },
                "status" to status.ifBlank { null },
                "order_status" to orderStatus.ifBlank { null },
            ),
            noCache = true,
        )
        val payload = unwrapData(root) as? JsonObject ?: return emptyList()
        val items = payload["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { runCatching { json.decodeFromJsonElement<AdminNfcTag>(it) }.getOrNull() }
    }

    suspend fun getNfcTagDetail(tagId: Long): AdminNfcTag? {
        val root = executeJson("GET", "/admin/machine-care/nfc-tags/$tagId", noCache = true)
        val data = unwrapData(root)
        if (data is JsonNull) return null
        return json.decodeFromJsonElement<AdminNfcTag>(data)
    }

    suspend fun getNfcTagActivationCode(tagId: Long): String {
        val root = executeJson("GET", "/admin/machine-care/nfc-tags/$tagId/activation-code", noCache = true)
        val data = unwrapData(root) as? JsonObject ?: return ""
        return data["activation_code"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    }

    suspend fun createNfcTagActivationCode(tagId: Long, phone: String): String {
        val body = buildJsonObject { put("phone", phone.trim()) }
        val root = executeJson("POST", "/admin/machine-care/nfc-tags/$tagId/activation-code", jsonBody = body)
        val data = unwrapData(root) as? JsonObject ?: return ""
        return data["activation_code"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    }

    suspend fun createNfcTagBatch(quantity: Int): List<AdminNfcTag> {
        val body = buildJsonObject { put("count", quantity) }
        val root = executeJson("POST", "/admin/machine-care/nfc-tags/batch", jsonBody = body)
        val data = unwrapData(root) as? JsonArray ?: return emptyList()
        return data.mapNotNull { runCatching { json.decodeFromJsonElement<AdminNfcTag>(it) }.getOrNull() }
    }

    suspend fun createFullAccessActivationCodes(quantity: Int): List<FullAccessCodeRow> {
        val body = buildJsonObject { put("count", quantity) }
        val root = executeJson("POST", "/admin/machine-care/nfc-tags/full-access-codes", jsonBody = body)
        val data = unwrapData(root) as? JsonArray ?: return emptyList()
        return data.mapNotNull { runCatching { json.decodeFromJsonElement<FullAccessCodeRow>(it) }.getOrNull() }
    }

    suspend fun getNfcTagFeatureDefaults(): List<String> {
        val root = executeJson("GET", "/admin/machine-care/nfc-tags/feature-defaults", noCache = true)
        val data = unwrapData(root) as? JsonObject ?: return emptyList()
        val arr = data["features"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    suspend fun updateNfcTagFeatureDefaults(features: List<String>): List<String> {
        val body = buildJsonObject {
            put("features", buildJsonArray { features.forEach { add(JsonPrimitive(it)) } })
        }
        val root = executeJson("PUT", "/admin/machine-care/nfc-tags/feature-defaults", jsonBody = body)
        val data = unwrapData(root) as? JsonObject ?: return features
        val arr = data["features"] as? JsonArray ?: return features
        return arr.mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    suspend fun updateNfcTagFeaturesBulk(tagIds: List<Long>, features: List<String>) {
        val body = buildJsonObject {
            put("tag_ids", buildJsonArray { tagIds.forEach { add(JsonPrimitive(it)) } })
            put("features", buildJsonArray { features.forEach { add(JsonPrimitive(it)) } })
        }
        executeJson("PUT", "/admin/machine-care/nfc-tags/features/bulk", jsonBody = body)
    }

    suspend fun enableNfcTag(tagId: Long) {
        executeJson("POST", "/admin/machine-care/nfc-tags/$tagId/enable")
    }

    suspend fun disableNfcTag(tagId: Long) {
        executeJson("POST", "/admin/machine-care/nfc-tags/$tagId/disable")
    }

    suspend fun transferNfcTag(tagId: Long, phone: String) {
        val body = buildJsonObject { put("phone", phone.trim()) }
        executeJson("POST", "/admin/machine-care/nfc-tags/$tagId/transfer", jsonBody = body)
    }

    suspend fun separateNfcTagAccount(tagId: Long) {
        executeJson("POST", "/admin/machine-care/nfc-tags/$tagId/separate-account")
    }

    suspend fun attachLegacyNfcTag(tagId: Long, machineId: String) {
        val parsed = machineId.trim().toLongOrNull()
        val body = buildJsonObject {
            if (parsed != null) put("machine_id", parsed) else put("machine_id", machineId.trim())
        }
        executeJson("POST", "/admin/machine-care/nfc-tags/$tagId/attach", jsonBody = body)
    }

    suspend fun attachPrecreatedNfcTagToOrder(tagId: Long, orderReference: String) {
        val body = buildJsonObject { put("order_reference", orderReference.trim()) }
        executeJson("POST", "/admin/machine-care/nfc-tags/$tagId/attach-order", jsonBody = body)
    }

    suspend fun getNfcTagActivationMessage(tagId: Long): String {
        val root = executeJson("GET", "/admin/machine-care/nfc-tags/$tagId/pancake-message")
        val data = unwrapData(root) as? JsonObject ?: return ""
        return data["message"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    }

    suspend fun syncNfcTagActivationToPancake(tagId: Long): PancakeActivationSync? {
        val root = executeJson("POST", "/admin/machine-care/nfc-tags/$tagId/pancake-sync")
        val data = unwrapData(root)
        if (data is JsonNull) return null
        return runCatching { json.decodeFromJsonElement<PancakeActivationSync>(data) }.getOrNull()
    }

    suspend fun syncAllPendingNfcActivationsToPancake(
        limit: Int = 10,
        retryFailed: Boolean = false,
        retryFailedBefore: String = "",
    ): PancakeSyncAllResult {
        val root = executeJson(
            method = "POST",
            path = "/admin/machine-care/nfc-tags/pancake-sync/all",
            query = mapOf(
                "limit" to limit.toString(),
                "retry_failed" to retryFailed.toString(),
                "retry_failed_before" to retryFailedBefore.ifBlank { null },
            ),
        )
        val data = unwrapData(root)
        return runCatching { json.decodeFromJsonElement<PancakeSyncAllResult>(data) }
            .getOrDefault(PancakeSyncAllResult())
    }

    suspend fun exportNfcTagsCsv(
        search: String = "",
        status: String = "",
        orderStatus: String = "",
    ): ByteArray = executeRaw(
        method = "GET",
        path = "/admin/machine-care/nfc-tags/export",
        query = mapOf(
            "q" to search.trim().ifBlank { null },
            "status" to status.ifBlank { null },
            "order_status" to orderStatus.ifBlank { null },
        ),
    )

    suspend fun exportSelectedNfcTagsCsv(tagIds: List<Long>): ByteArray {
        val body = buildJsonObject {
            put("tag_ids", buildJsonArray { tagIds.forEach { add(JsonPrimitive(it)) } })
        }.toString().toRequestBody(jsonMediaType)
        return executeRaw("POST", "/admin/machine-care/nfc-tags/export-selection", body = body)
    }

    suspend fun saveNfcProgrammingLog(
        tagId: Long?,
        outcome: String,
        userAgent: String,
        entries: List<String>,
    ) {
        val body = buildJsonObject {
            if (tagId != null) put("tag_id", tagId)
            put("outcome", outcome)
            put("user_agent", userAgent)
            put("entries", buildJsonArray { entries.forEach { add(JsonPrimitive(it)) } })
        }
        runCatching {
            executeJson("POST", "/admin/machine-care/nfc-tags/programming-logs", jsonBody = body)
        }
    }

    // ==================== GUIDE CONTENT ====================

    suspend fun getNfcGuideContent(): NfcGuideContent {
        val root = executeJson("GET", "/admin/machine-care/nfc-tags/guide-content", noCache = true)
        val data = unwrapData(root) as? JsonObject ?: return NfcGuideContent.DEFAULT
        val contentObj = data["content"] as? JsonObject ?: return NfcGuideContent.DEFAULT
        return mergeGuideContent(contentObj)
    }

    suspend fun updateNfcGuideContent(diffPayload: JsonObject) {
        executeJson("PUT", "/admin/machine-care/nfc-tags/guide-content", jsonBody = diffPayload)
    }

    private fun mergeGuideContent(obj: JsonObject): NfcGuideContent {
        fun str(key: String, fallback: String): String =
            obj[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

        val taglines = (obj["tagline"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
            ?.take(3)
            ?.takeIf { it.isNotEmpty() }
            ?: NfcGuideContent.DEFAULT_TAGLINE

        return NfcGuideContent(
            title = str("title", NfcGuideContent.DEFAULT_TITLE),
            subtitle = str("subtitle", NfcGuideContent.DEFAULT_SUBTITLE),
            forLabel = str("for_label", NfcGuideContent.DEFAULT_FOR_LABEL),
            tagline = taglines,
            step1Title = str("step1_title", NfcGuideContent.DEFAULT_STEP1_TITLE),
            step1Text = str("step1_text", NfcGuideContent.DEFAULT_STEP1_TEXT),
            step2Title = str("step2_title", NfcGuideContent.DEFAULT_STEP2_TITLE),
            step2Text = str("step2_text", NfcGuideContent.DEFAULT_STEP2_TEXT),
            step3Title = str("step3_title", NfcGuideContent.DEFAULT_STEP3_TITLE),
            step3Text = str("step3_text", NfcGuideContent.DEFAULT_STEP3_TEXT),
            phone = str("phone", NfcGuideContent.DEFAULT_PHONE),
            foot1 = str("foot1", NfcGuideContent.DEFAULT_FOOT1),
            foot2 = str("foot2", NfcGuideContent.DEFAULT_FOOT2),
            wish = str("wish", NfcGuideContent.DEFAULT_WISH),
        )
    }

    // ==================== MACHINE PROFILE ====================

    suspend fun getNfcTagMachineProfile(tagId: Long): MachineProfileResponse {
        val root = executeJson("GET", "/admin/machine-care/nfc-tags/$tagId/machine-profile", noCache = true)
        val data = unwrapData(root)
        return json.decodeFromJsonElement<MachineProfileResponse>(data)
    }

    suspend fun searchNfcMachineProfileCustomers(q: String): List<MachineOwner> {
        val root = executeJson(
            method = "GET",
            path = "/admin/machine-care/nfc-tags/machine-profile-customers",
            query = mapOf("q" to q.trim()),
        )
        val data = unwrapData(root) as? JsonArray ?: return emptyList()
        return data.mapNotNull { runCatching { json.decodeFromJsonElement<MachineOwner>(it) }.getOrNull() }
    }

    suspend fun updateNfcTagMachineProfile(tagId: Long, payload: JsonObject) {
        executeJson("PUT", "/admin/machine-care/nfc-tags/$tagId/machine-profile", jsonBody = payload)
    }

    suspend fun listMachineBrands(): List<MachineBrand> {
        val root = executeJson(
            method = "GET",
            path = "/admin/machine-care/machine-brands",
            query = mapOf("page" to "1", "limit" to "200"),
        )
        val data = unwrapData(root) as? JsonArray ?: return emptyList()
        return data.mapNotNull { runCatching { json.decodeFromJsonElement<MachineBrand>(it) }.getOrNull() }
    }

    suspend fun listMachineModels(brandId: Long): List<MachineModel> {
        val root = executeJson(
            method = "GET",
            path = "/admin/machine-care/machine-models",
            query = mapOf("page" to "1", "limit" to "200", "brand_id" to brandId.toString()),
        )
        val data = unwrapData(root) as? JsonArray ?: return emptyList()
        return data.mapNotNull { runCatching { json.decodeFromJsonElement<MachineModel>(it) }.getOrNull() }
    }

    suspend fun listMachineUsageOptions(): List<MachineOption> {
        val root = executeJson(
            method = "GET",
            path = "/admin/machine-care/machine-usage-options",
            query = mapOf("page" to "1", "limit" to "200", "active" to "true"),
        )
        val data = unwrapData(root) as? JsonArray ?: return emptyList()
        return data.mapNotNull { runCatching { json.decodeFromJsonElement<MachineOption>(it) }.getOrNull() }
    }

    suspend fun listMachineConditionOptions(): List<MachineOption> {
        val root = executeJson(
            method = "GET",
            path = "/admin/machine-care/machine-condition-options",
            query = mapOf("page" to "1", "limit" to "200", "active" to "true"),
        )
        val data = unwrapData(root) as? JsonArray ?: return emptyList()
        return data.mapNotNull { runCatching { json.decodeFromJsonElement<MachineOption>(it) }.getOrNull() }
    }
}
