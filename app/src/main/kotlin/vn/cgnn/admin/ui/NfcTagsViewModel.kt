package vn.cgnn.admin.ui

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import vn.cgnn.admin.BuildConfig
import vn.cgnn.admin.CgnnAdminApp
import vn.cgnn.admin.data.model.AdminNfcTag
import vn.cgnn.admin.data.model.AppFeatures
import vn.cgnn.admin.data.model.LoginDeviceInfo
import vn.cgnn.admin.data.model.NfcGuideCardData
import vn.cgnn.admin.data.model.NfcGuideContent
import vn.cgnn.admin.data.network.AdminSessionExpiredException
import vn.cgnn.admin.data.storage.NfcListPrefs
import vn.cgnn.admin.util.AdminUtils
import vn.cgnn.admin.util.NfcGuideHtmlBuilder
import java.time.LocalDate

enum class AuthState { Checking, LoggedOut, Authenticated }

data class ToastEvent(
    val message: String,
    val isError: Boolean = false,
)

data class DetailExtraState(
    val tagId: Long = -1L,
    val loading: Boolean = false,
    val loginDevice: LoginDeviceInfo? = null,
    val activationCode: String = "",
)

sealed class UiEffect {
    data class Toast(val message: String, val isError: Boolean = false) : UiEffect()
    data class CopyText(val label: String, val text: String, val successMsg: String) : UiEffect()
    data class ShareFile(val filename: String, val mimeType: String, val bytes: ByteArray, val successMsg: String) : UiEffect()
    data class PrintHtml(val html: String, val jobName: String) : UiEffect()
}

data class NfcTagsUiState(
    val authState: AuthState = AuthState.Checking,
    val savedEmail: String = "",
    val loginLoading: Boolean = false,
    val loginError: String? = null,

    val rows: List<AdminNfcTag> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val copyingTagId: Long? = null,
    val pancakeCopyingTagId: Long? = null,
    val guidePrinting: Boolean = false,
    val bulkPancakeSyncing: Boolean = false,
    val bulkPancakeProgress: String = "",

    val search: String = "",
    val status: String = "",
    val orderStatus: String = "",
    val attnOnly: Boolean = false,
    val searchScoped: Boolean = false,

    val selectedTagIds: List<Long> = emptyList(),
    val deselectAfterProgramming: Boolean = true,
    val deselectAfterPrinting: Boolean = true,
    val showProgramButton: Boolean = true,
    val showGuideButton: Boolean = true,

    val defaultFeatures: List<String> = AppFeatures.ALL_KEYS,
    val defaultFeaturesLoading: Boolean = true,

    val detailTagId: Long? = null,
    val detailExtra: DetailExtraState = DetailExtraState(),

    val programmingQueue: List<AdminNfcTag> = emptyList(),
    val machineProfileTag: AdminNfcTag? = null,
) {
    val searchActive: Boolean get() = search.trim().isNotEmpty()
    val filtersIgnored: Boolean get() = searchActive && !searchScoped
    val effStatus: String get() = if (filtersIgnored) "" else status
    val effOrderStatus: String get() = if (filtersIgnored) "" else orderStatus
    val effAttnOnly: Boolean get() = if (filtersIgnored) false else attnOnly
    val hasActiveFilter: Boolean get() = status.isNotEmpty() || orderStatus.isNotEmpty() || attnOnly || searchActive

    val attentionCount: Int get() = rows.count { it.needsAttention }
    val shownRows: List<AdminNfcTag> get() = if (effAttnOnly) rows.filter { it.needsAttention } else rows
    val allVisibleSelected: Boolean
        get() = shownRows.isNotEmpty() && shownRows.all { it.id in selectedTagIds }
}

class NfcTagsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CgnnAdminApp
    val api = app.apiClient
    private val prefs = app.preferences

    private val _state = MutableStateFlow(NfcTagsUiState())
    val state: StateFlow<NfcTagsUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    private val knownTags = LinkedHashMap<Long, AdminNfcTag>()
    private var loadJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var detailJob: Job? = null
    private var autoRefreshJob: Job? = null

    private val activationCodeRegex = Regex("^\\d{4}[A-Z]{4}$")

    init {
        api.onSessionExpired = {
            _state.update {
                it.copy(
                    authState = AuthState.LoggedOut,
                    loginError = "Phiên đăng nhập đã hết hạn. Bác vui lòng đăng nhập lại.",
                )
            }
            autoRefreshJob?.cancel()
        }

        val savedPrefs = prefs.loadListPrefs()
        _state.update {
            it.copy(
                savedEmail = prefs.getSavedEmail(),
                search = savedPrefs.search,
                status = savedPrefs.status,
                orderStatus = savedPrefs.orderStatus,
                selectedTagIds = savedPrefs.selectedTagIds,
                deselectAfterProgramming = savedPrefs.deselectAfterProgramming,
                deselectAfterPrinting = savedPrefs.deselectAfterPrinting,
                showProgramButton = savedPrefs.showProgramButton,
                showGuideButton = savedPrefs.showGuideButton,
            )
        }

        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            val ok = api.restoreSession()
            if (ok) {
                _state.update { it.copy(authState = AuthState.Authenticated, loginError = null) }
                onAuthenticated()
            } else {
                _state.update { it.copy(authState = AuthState.LoggedOut) }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(loginLoading = true, loginError = null) }
            try {
                api.login(email, password)
                _state.update {
                    it.copy(
                        authState = AuthState.Authenticated,
                        loginLoading = false,
                        loginError = null,
                        savedEmail = email.trim(),
                    )
                }
                onAuthenticated()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loginLoading = false,
                        loginError = e.message ?: "Đăng nhập không thành công.",
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            autoRefreshJob?.cancel()
            api.logout()
            knownTags.clear()
            _state.update {
                it.copy(
                    authState = AuthState.LoggedOut,
                    rows = emptyList(),
                    detailTagId = null,
                    programmingQueue = emptyList(),
                    machineProfileTag = null,
                    loginError = null,
                )
            }
        }
    }

    private fun onAuthenticated() {
        loadDefaultFeatures()
        loadTags(background = false)
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                if (_state.value.authState == AuthState.Authenticated &&
                    _state.value.programmingQueue.isEmpty()
                ) {
                    loadTags(background = true)
                }
            }
        }
    }

    private fun persistListPrefs() {
        val s = _state.value
        prefs.saveListPrefs(
            NfcListPrefs(
                search = s.search,
                status = s.status,
                orderStatus = s.orderStatus,
                selectedTagIds = s.selectedTagIds,
                deselectAfterProgramming = s.deselectAfterProgramming,
                deselectAfterPrinting = s.deselectAfterPrinting,
                showProgramButton = s.showProgramButton,
                showGuideButton = s.showGuideButton,
            ),
        )
    }

    fun emitToast(message: String, isError: Boolean = false) {
        _effects.tryEmit(UiEffect.Toast(message, isError))
    }

    private fun loadDefaultFeatures() {
        viewModelScope.launch {
            _state.update { it.copy(defaultFeaturesLoading = true) }
            try {
                val features = api.getNfcTagFeatureDefaults()
                if (features.isNotEmpty()) {
                    _state.update { it.copy(defaultFeatures = features) }
                }
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không tải được quyền mặc định của thẻ.", isError = true)
                }
            } finally {
                _state.update { it.copy(defaultFeaturesLoading = false) }
            }
        }
    }

    fun loadTags(background: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            if (!background) _state.update { it.copy(loading = true) }
            try {
                val nextRows = api.listNfcTags(
                    search = s.search.trim(),
                    status = s.effStatus,
                    orderStatus = s.effOrderStatus,
                    limit = 100,
                )
                nextRows.forEach { knownTags[it.id] = it }
                _state.update { it.copy(rows = nextRows, loading = false) }
            } catch (e: Exception) {
                if (!background && e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không tải được danh sách thẻ kích hoạt máy.", isError = true)
                }
                if (!background) _state.update { it.copy(loading = false) }
            }
        }
    }

    // ==================== FILTERS & SEARCH ====================

    fun updateSearch(value: String) {
        _state.update { it.copy(search = value) }
        persistListPrefs()
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(if (value.trim().isNotEmpty()) 300L else 0L)
            loadTags(background = false)
        }
    }

    fun toggleSearchScoped() {
        _state.update { it.copy(searchScoped = !it.searchScoped) }
        loadTags(background = false)
    }

    fun updateStatusFilter(status: String) {
        _state.update { it.copy(status = status) }
        persistListPrefs()
        loadTags(background = false)
    }

    fun updateOrderStatusFilter(orderStatus: String) {
        _state.update { it.copy(orderStatus = orderStatus) }
        persistListPrefs()
        loadTags(background = false)
    }

    fun toggleAttentionOnly() {
        _state.update { it.copy(attnOnly = !it.attnOnly) }
    }

    fun clearFilters() {
        _state.update { it.copy(search = "", status = "", orderStatus = "", attnOnly = false) }
        persistListPrefs()
        loadTags(background = false)
    }

    // ==================== SELECTION ====================

    fun toggleTagSelection(tagId: Long) {
        _state.update { s ->
            val next = if (tagId in s.selectedTagIds) {
                s.selectedTagIds - tagId
            } else {
                s.selectedTagIds + tagId
            }
            s.copy(selectedTagIds = next)
        }
        persistListPrefs()
    }

    fun toggleAllVisible() {
        _state.update { s ->
            val visibleIds = s.shownRows.map { it.id }
            val next = if (s.allVisibleSelected) {
                s.selectedTagIds.filterNot { it in visibleIds }
            } else {
                (s.selectedTagIds + visibleIds).distinct()
            }
            s.copy(selectedTagIds = next)
        }
        persistListPrefs()
    }

    fun clearSelection() {
        _state.update { it.copy(selectedTagIds = emptyList()) }
        persistListPrefs()
    }

    fun removeFromSelection(tagIds: Collection<Long>) {
        val set = tagIds.toSet()
        _state.update { it.copy(selectedTagIds = it.selectedTagIds.filterNot { id -> id in set }) }
        persistListPrefs()
    }

    fun getSelectedProgrammableTags(): List<AdminNfcTag> =
        _state.value.selectedTagIds.mapNotNull { knownTags[it] }.filter { it.canProgram }

    fun getUnprintableSelectedIds(): List<Long> =
        _state.value.selectedTagIds.filter { knownTags[it]?.canProgram != true }

    fun getKnownTag(tagId: Long): AdminNfcTag? =
        _state.value.rows.firstOrNull { it.id == tagId } ?: knownTags[tagId]

    fun setDeselectAfterProgramming(enabled: Boolean) {
        _state.update { it.copy(deselectAfterProgramming = enabled) }
        persistListPrefs()
    }

    fun setDeselectAfterPrinting(enabled: Boolean) {
        _state.update { it.copy(deselectAfterPrinting = enabled) }
        persistListPrefs()
    }

    fun setShowProgramButton(visible: Boolean) {
        _state.update { it.copy(showProgramButton = visible) }
        persistListPrefs()
    }

    fun setShowGuideButton(visible: Boolean) {
        _state.update { it.copy(showGuideButton = visible) }
        persistListPrefs()
    }

    // ==================== DETAIL ====================

    fun openDetail(tagId: Long) {
        _state.update {
            it.copy(
                detailTagId = tagId,
                detailExtra = DetailExtraState(tagId = tagId, loading = true),
            )
        }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            coroutineScope {
                val detailDef = async { runCatching { api.getNfcTagDetail(tagId) }.getOrNull() }
                val codeDef = async { runCatching { api.getNfcTagActivationCode(tagId) }.getOrNull().orEmpty() }
                val detail = detailDef.await()
                val rawCode = codeDef.await()
                if (detail != null) knownTags[detail.id] = detail
                _state.update { s ->
                    if (s.detailTagId != tagId) s else s.copy(
                        detailExtra = DetailExtraState(
                            tagId = tagId,
                            loading = false,
                            loginDevice = detail?.latestLoginDevice,
                            activationCode = if (activationCodeRegex.matches(rawCode)) rawCode else "",
                        ),
                    )
                }
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _state.update { it.copy(detailTagId = null) }
    }

    // ==================== PROGRAMMING ====================

    fun startProgramming(tags: List<AdminNfcTag>) {
        if (tags.isEmpty()) return
        closeDetail()
        _state.update { it.copy(programmingQueue = tags) }
    }

    fun closeProgramming() {
        _state.update { it.copy(programmingQueue = emptyList()) }
    }

    fun onTagProgrammedAndVerified(tag: AdminNfcTag) {
        if (_state.value.deselectAfterProgramming) {
            removeFromSelection(listOf(tag.id))
        }
    }

    fun sendProgrammingLog(tagId: Long?, outcome: String, entries: List<String>) {
        viewModelScope.launch {
            val ua = "CGNN-Admin-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL})"
            api.saveNfcProgrammingLog(tagId, outcome, ua, entries)
        }
    }

    // ==================== MACHINE PROFILE ====================

    fun openMachineProfile(tag: AdminNfcTag) {
        closeDetail()
        _state.update { it.copy(machineProfileTag = tag) }
    }

    fun closeMachineProfile() {
        _state.update { it.copy(machineProfileTag = null) }
    }

    // ==================== MUTATIONS ====================

    private fun runAction(
        successMessage: String,
        fallbackError: String = "Không thực hiện được thao tác.",
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
                onSuccess?.invoke()
                emitToast(successMessage, isError = false)
                loadTags(background = false)
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: fallbackError, isError = true)
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun createBatch(quantity: Int) {
        if (quantity !in 1..500) {
            emitToast("Số thẻ cần tạo phải là số nguyên từ 1 đến 500.", isError = true)
            return
        }
        runAction(successMessage = "Đã tạo $quantity thẻ kích hoạt máy.") {
            api.createNfcTagBatch(quantity)
        }
    }

    fun createAndShareFullAccessCodes() {
        val count = 12
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val rows = api.createFullAccessActivationCodes(count)
                if (rows.isEmpty()) {
                    emitToast("Chưa tạo được mã kích hoạt. Bác thử lại nhé.", isError = true)
                    return@launch
                }
                val jsonLines = rows.joinToString(",\n") { "  ${Json.encodeToString(it.activationCode)}" }
                val content = "[\n$jsonLines\n]\n"
                val filename = "ma-kich-hoat-full-quyen-${LocalDate.now()}.json"
                _effects.tryEmit(
                    UiEffect.ShareFile(
                        filename = filename,
                        mimeType = "application/json",
                        bytes = content.toByteArray(Charsets.UTF_8),
                        successMsg = "Đã tạo ${rows.size} mã kích hoạt và mở chia sẻ file.",
                    ),
                )
                loadTags(background = false)
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không tạo được mã kích hoạt. Bác thử lại nhé.", isError = true)
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun saveDefaultFeatures(features: List<String>, onDone: () -> Unit) {
        if (features.isEmpty()) {
            emitToast("Thẻ mới phải có ít nhất một quyền sử dụng.", isError = true)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val updated = api.updateNfcTagFeatureDefaults(features)
                _state.update { it.copy(defaultFeatures = updated.ifEmpty { features }) }
                emitToast("Đã lưu quyền mặc định cho thẻ mới.")
                onDone()
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không lưu được quyền mặc định cho thẻ mới.", isError = true)
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun updateTagFeatures(tagIds: List<Long>, features: List<String>, onDone: () -> Unit) {
        if (features.isEmpty()) {
            emitToast("Thẻ phải có ít nhất một quyền sử dụng.", isError = true)
            return
        }
        if (tagIds.size > 200) {
            emitToast("Mỗi lần chỉ cập nhật được tối đa 200 thẻ. Bác bỏ chọn bớt thẻ nhé.", isError = true)
            return
        }
        val msg = if (tagIds.size == 1) "Đã cập nhật quyền cho thẻ." else "Đã cập nhật quyền cho ${tagIds.size} thẻ."
        runAction(successMessage = msg, onSuccess = onDone) {
            api.updateNfcTagFeaturesBulk(tagIds, features)
        }
    }

    fun enableTag(tag: AdminNfcTag) {
        runAction(successMessage = "Đã bật lại thẻ.") {
            api.enableNfcTag(tag.id)
        }
    }

    fun disableTag(tag: AdminNfcTag, onDone: () -> Unit) {
        runAction(successMessage = "Đã tắt thẻ.", onSuccess = onDone) {
            api.disableNfcTag(tag.id)
        }
    }

    fun transferTag(tag: AdminNfcTag, phone: String, onDone: () -> Unit) {
        if (phone.trim().isEmpty()) return
        runAction(
            successMessage = "Đã chuyển thẻ; mã và đường dẫn cũ đã bị thu hồi. Hãy chép mã, đường dẫn mới rồi ghi lại thẻ.",
            onSuccess = onDone,
        ) {
            api.transferNfcTag(tag.id, phone)
        }
    }

    fun separateTagAccount(tag: AdminNfcTag, onDone: () -> Unit) {
        runAction(
            successMessage = "Đã tách thẻ sang tài khoản riêng. Khách có thể nhập lại mã kích hoạt này trên máy khác mà không bị đá nhau.",
            onSuccess = onDone,
        ) {
            api.separateNfcTagAccount(tag.id)
        }
    }

    fun attachLegacyMachine(tag: AdminNfcTag, machineId: String, onDone: () -> Unit) {
        if (machineId.trim().isEmpty()) return
        runAction(successMessage = "Đã gắn thẻ vào máy cũ.", onSuccess = onDone) {
            api.attachLegacyNfcTag(tag.id, machineId)
        }
    }

    fun attachOrder(tag: AdminNfcTag, orderReference: String, onDone: () -> Unit) {
        val ref = orderReference.trim()
        if (ref.isEmpty()) {
            emitToast("Bác hãy nhập mã đơn mua thẻ.", isError = true)
            return
        }
        runAction(successMessage = "Đã gắn ${tag.codeLabel} vào đơn $ref.", onSuccess = onDone) {
            api.attachPrecreatedNfcTagToOrder(tag.id, ref)
        }
    }

    fun addActivationPhone(tag: AdminNfcTag, phone: String, onDone: () -> Unit) {
        val clean = phone.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, copyingTagId = tag.id) }
            try {
                val code = api.createNfcTagActivationCode(tag.id, clean)
                if (!activationCodeRegex.matches(code)) {
                    throw IllegalStateException("Chưa tạo được mã kích hoạt. Bác thử lại nhé.")
                }
                _effects.tryEmit(
                    UiEffect.CopyText(
                        label = "Mã kích hoạt ${tag.codeLabel}",
                        text = code,
                        successMsg = "Đã lưu số điện thoại và sao chép mã kích hoạt của ${tag.codeLabel}.",
                    ),
                )
                onDone()
                loadTags(background = true)
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không thêm được số điện thoại cho thẻ.", isError = true)
                }
            } finally {
                _state.update { it.copy(busy = false, copyingTagId = null) }
            }
        }
    }

    // ==================== COPY & SHARE ====================

    fun copyLandMeasureTrialLink() {
        val url = "${BuildConfig.FRONTEND_URL.trimEnd('/')}/thu-do-dat"
        _effects.tryEmit(
            UiEffect.CopyText(
                label = "Link đo đất dùng thử",
                text = url,
                successMsg = "Đã sao chép link đo đất dùng thử.",
            ),
        )
    }

    fun copyActivationCode(tag: AdminNfcTag) {
        viewModelScope.launch {
            _state.update { it.copy(copyingTagId = tag.id) }
            try {
                val code = api.getNfcTagActivationCode(tag.id)
                if (!activationCodeRegex.matches(code)) {
                    throw IllegalStateException("Thẻ chưa có mã kích hoạt. Hãy kiểm tra số điện thoại của đơn mua.")
                }
                _effects.tryEmit(
                    UiEffect.CopyText(
                        label = "Mã kích hoạt ${tag.codeLabel}",
                        text = code,
                        successMsg = "Đã sao chép mã kích hoạt của ${tag.codeLabel}.",
                    ),
                )
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không sao chép được mã kích hoạt.", isError = true)
                }
            } finally {
                _state.update { it.copy(copyingTagId = null) }
            }
        }
    }

    fun copyTagLink(tag: AdminNfcTag) {
        viewModelScope.launch {
            _state.update { it.copy(copyingTagId = tag.id) }
            try {
                val detail = api.getNfcTagDetail(tag.id)
                val url = detail?.url?.trim().orEmpty()
                if (url.isEmpty()) throw IllegalStateException("Không tìm thấy liên kết của thẻ.")
                _effects.tryEmit(
                    UiEffect.CopyText(
                        label = "Link ${tag.codeLabel}",
                        text = url,
                        successMsg = "Đã sao chép link của ${tag.codeLabel}.",
                    ),
                )
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không sao chép được link của thẻ.", isError = true)
                }
            } finally {
                _state.update { it.copy(copyingTagId = null) }
            }
        }
    }

    fun copyPancakeMessage(tag: AdminNfcTag) {
        viewModelScope.launch {
            _state.update { it.copy(pancakeCopyingTagId = tag.id) }
            try {
                val content = api.getNfcTagActivationMessage(tag.id)
                if (content.isEmpty()) throw IllegalStateException("Không tìm thấy nội dung cần gửi.")
                _effects.tryEmit(
                    UiEffect.CopyText(
                        label = "Tin nhắn ${tag.codeLabel}",
                        text = content,
                        successMsg = "Đã sao chép nội dung gửi cho ${tag.codeLabel}.",
                    ),
                )
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không sao chép được nội dung gửi thủ công.", isError = true)
                }
            } finally {
                _state.update { it.copy(pancakeCopyingTagId = null) }
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val s = _state.value
                val bytes = if (s.selectedTagIds.isNotEmpty()) {
                    api.exportSelectedNfcTagsCsv(s.selectedTagIds)
                } else {
                    api.exportNfcTagsCsv(s.search, s.effStatus, s.effOrderStatus)
                }
                _effects.tryEmit(
                    UiEffect.ShareFile(
                        filename = "the-nfc-cgnn-nxp-tagwriter-no-description.csv",
                        mimeType = "text/csv",
                        bytes = bytes,
                        successMsg = "Đã tải file URL và QR của thẻ.",
                    ),
                )
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không xuất được file thẻ.", isError = true)
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    // ==================== PANCAKE SYNC ALL ====================

    fun syncAllPancake() {
        if (_state.value.bulkPancakeSyncing) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    bulkPancakeSyncing = true,
                    bulkPancakeProgress = "Đang tìm các thẻ chưa đồng bộ",
                )
            }
            try {
                var totalProcessed = 0
                var totalSucceeded = 0
                var totalFailed = 0
                var totalManual = 0
                var retryFailedBefore = ""
                var retryExistingFailures = true

                while (true) {
                    val res = api.syncAllPendingNfcActivationsToPancake(
                        limit = 10,
                        retryFailed = retryExistingFailures,
                        retryFailedBefore = retryFailedBefore,
                    )
                    retryExistingFailures = false
                    retryFailedBefore = res.retryFailedBefore ?: retryFailedBefore
                    totalProcessed += res.processed
                    totalSucceeded += res.succeeded
                    totalFailed += res.failed
                    totalManual += res.manualRequired

                    val progressText = if (res.remaining > 0) {
                        "Đã xử lý $totalProcessed đơn, còn ${res.remaining} đơn"
                    } else {
                        "Đã xử lý $totalProcessed đơn"
                    }
                    _state.update { it.copy(bulkPancakeProgress = progressText) }
                    if (res.remaining <= 0 || res.processed <= 0) break
                }

                val needsChecking = totalFailed + totalManual
                val msg = when {
                    totalProcessed == 0 -> "Hiện không có thẻ cũ nào cần đồng bộ thêm."
                    needsChecking > 0 -> "Đã đồng bộ $totalSucceeded đơn. Có $needsChecking đơn cần kiểm tra."
                    else -> "Đã đồng bộ xong $totalSucceeded đơn sang Pancake."
                }
                emitToast(msg, isError = false)
                loadTags(background = false)
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không đồng bộ hết được với Pancake. Bác hãy thử lại.", isError = true)
                }
            } finally {
                _state.update { it.copy(bulkPancakeSyncing = false, bulkPancakeProgress = "") }
            }
        }
    }

    // ==================== PRINT GUIDES ====================

    fun printSelectedGuides() {
        val tags = getSelectedProgrammableTags()
        if (tags.isEmpty()) {
            emitToast("Các thẻ đã chọn không in được hướng dẫn. Bác chọn thẻ khác nhé.", isError = true)
            return
        }
        if (tags.size > NfcGuideContent.MAX_CARDS) {
            emitToast("Mỗi lần chỉ in được tối đa ${NfcGuideContent.MAX_CARDS} thẻ. Bác bỏ chọn bớt thẻ nhé.", isError = true)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(guidePrinting = true) }
            try {
                val guideContent = api.getNfcGuideContent()
                val cards = mutableListOf<NfcGuideCardData>()
                val printedIds = mutableListOf<Long>()
                val skipped = mutableListOf<String>()

                for (chunk in tags.chunked(4)) {
                    val part = coroutineScope {
                        chunk.map { tag ->
                            async {
                                runCatching {
                                    val detailDef = async { api.getNfcTagDetail(tag.id) }
                                    val codeDef = async { api.getNfcTagActivationCode(tag.id) }
                                    val detail = detailDef.await()
                                    val code = codeDef.await()
                                    val link = detail?.url?.trim().orEmpty()
                                    if (link.isEmpty() || !activationCodeRegex.matches(code)) {
                                        error("thiếu link hoặc mã")
                                    }
                                    NfcGuideCardData(
                                        tagId = tag.id,
                                        name = (tag.orderRecipientName ?: detail?.orderRecipientName).orEmpty().trim(),
                                        code = code,
                                        link = link,
                                    )
                                }.getOrNull().also {
                                    if (it == null) skipped.add(tag.codeLabel)
                                }
                            }
                        }.awaitAll()
                    }
                    part.filterNotNull().forEach { card ->
                        cards.add(card)
                        printedIds.add(card.tagId)
                    }
                }

                if (cards.isEmpty()) {
                    emitToast("Chưa lấy được mã kích hoạt của các thẻ đã chọn. Thẻ cần có số điện thoại của đơn mua nhé.", isError = true)
                    return@launch
                }
                if (skipped.isNotEmpty()) {
                    emitToast("Chưa in được ${skipped.size} thẻ vì chưa có mã kích hoạt: ${skipped.joinToString(", ")}.", isError = true)
                }

                val html = NfcGuideHtmlBuilder.buildHtml(cards, guideContent)
                _effects.tryEmit(UiEffect.PrintHtml(html, "Huong-dan-the-NFC-${cards.size}-the"))

                if (_state.value.deselectAfterPrinting) {
                    removeFromSelection(printedIds)
                }
            } catch (e: Exception) {
                if (e !is AdminSessionExpiredException) {
                    emitToast(e.message ?: "Không chuẩn bị được tờ hướng dẫn. Bác thử lại nhé.", isError = true)
                }
            } finally {
                _state.update { it.copy(guidePrinting = false) }
            }
        }
    }
}
