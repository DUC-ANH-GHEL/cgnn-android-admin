package vn.cgnn.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.cgnn.admin.data.model.AdminNfcTag
import vn.cgnn.admin.data.model.AppFeatures
import vn.cgnn.admin.data.model.NfcGuideContent
import vn.cgnn.admin.data.model.PancakeOrderStatuses
import vn.cgnn.admin.data.model.TagStatusLabels
import vn.cgnn.admin.ui.components.ButtonSettingsSheet
import vn.cgnn.admin.ui.components.CloseVectorIcon
import vn.cgnn.admin.ui.components.CopyVectorIcon
import vn.cgnn.admin.ui.components.CreateBatchSheet
import vn.cgnn.admin.ui.components.DisableTagConfirmSheet
import vn.cgnn.admin.ui.components.FeaturesEditorSheet
import vn.cgnn.admin.ui.components.LinkVectorIcon
import vn.cgnn.admin.ui.components.LoginScreen
import vn.cgnn.admin.ui.components.MessageVectorIcon
import vn.cgnn.admin.ui.components.NfcGuideEditorSheet
import vn.cgnn.admin.ui.components.NfcMachineProfileSheet
import vn.cgnn.admin.ui.components.NfcProgrammingDialog
import vn.cgnn.admin.ui.components.NfcTagDetailSheet
import vn.cgnn.admin.ui.components.NfcWaveVectorIcon
import vn.cgnn.admin.ui.components.OrderFilterSheet
import vn.cgnn.admin.ui.components.SearchVectorIcon
import vn.cgnn.admin.ui.components.SeparateAccountConfirmSheet
import vn.cgnn.admin.ui.components.SingleInputActionSheet
import vn.cgnn.admin.ui.components.StatusFilterSheet
import vn.cgnn.admin.ui.components.ToolsSheet
import vn.cgnn.admin.ui.theme.LineActionButton
import vn.cgnn.admin.ui.theme.NfcColors
import vn.cgnn.admin.ui.theme.QuickCopyPill
import vn.cgnn.admin.ui.theme.SilverPrimaryButton
import vn.cgnn.admin.util.AdminUtils
import vn.cgnn.admin.util.NfcGuideHtmlBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgnnAdminRoot(viewModel: NfcTagsViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var lastToastIsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.Toast -> {
                    lastToastIsError = effect.isError
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = effect.message,
                        duration = if (effect.isError) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                }

                is UiEffect.CopyText -> {
                    AdminUtils.copyToClipboard(context, effect.label, effect.text)
                    lastToastIsError = false
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(effect.successMsg, duration = SnackbarDuration.Short)
                }

                is UiEffect.ShareFile -> {
                    runCatching {
                        AdminUtils.shareExportFile(context, effect.filename, effect.mimeType, effect.bytes)
                        lastToastIsError = false
                        snackbarHostState.showSnackbar(effect.successMsg, duration = SnackbarDuration.Short)
                    }.onFailure { err ->
                        lastToastIsError = true
                        snackbarHostState.showSnackbar(err.message ?: "Không mở được chia sẻ file.", duration = SnackbarDuration.Long)
                    }
                }

                is UiEffect.PrintHtml -> {
                    runCatching {
                        NfcGuideHtmlBuilder.printHtml(context, effect.html, effect.jobName)
                    }.onFailure { err ->
                        lastToastIsError = true
                        snackbarHostState.showSnackbar(err.message ?: "Không mở được trình in Android.", duration = SnackbarDuration.Long)
                    }
                }
            }
        }
    }

    when (state.authState) {
        AuthState.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NfcColors.PearlBackgroundBrush),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = NfcColors.DarkBarStart)
                    Text("Đang kiểm tra phiên đăng nhập...", fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
                }
            }
        }

        AuthState.LoggedOut -> {
            LoginScreen(
                initialEmail = state.savedEmail,
                loading = state.loginLoading,
                error = state.loginError,
                onLogin = { email, password -> viewModel.login(email, password) },
            )
        }

        AuthState.Authenticated -> {
            NfcTagsMainScreen(
                state = state,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                lastToastIsError = lastToastIsError,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcTagsMainScreen(
    state: NfcTagsUiState,
    viewModel: NfcTagsViewModel,
    snackbarHostState: SnackbarHostState,
    lastToastIsError: Boolean,
) {
    var statusSheetOpen by remember { mutableStateOf(false) }
    var orderSheetOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var createBatchOpen by remember { mutableStateOf(false) }
    var defaultsOpen by remember { mutableStateOf(false) }
    var buttonSettingsOpen by remember { mutableStateOf(false) }
    var guideEditorOpen by remember { mutableStateOf(false) }
    var moreSelectionActionsOpen by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var searchBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    var rootOriginInWindow by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissSearchFocus = remember(focusManager, keyboardController) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val listState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val showScrollToSearch by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    // Automatically dismiss focus & hide keyboard when scrolling or when search bar leaves viewport
    LaunchedEffect(listState.isScrollInProgress, showScrollToSearch) {
        if (searchFocused && (listState.isScrollInProgress || showScrollToSearch)) {
            dismissSearchFocus()
        }
    }

    var featureDialogTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var featureDialogInitial by remember { mutableStateOf<List<String>>(AppFeatures.ALL_KEYS) }
    var attachOrderTag by remember { mutableStateOf<AdminNfcTag?>(null) }
    var transferTag by remember { mutableStateOf<AdminNfcTag?>(null) }
    var legacyTag by remember { mutableStateOf<AdminNfcTag?>(null) }
    var separateTag by remember { mutableStateOf<AdminNfcTag?>(null) }
    var disableTag by remember { mutableStateOf<AdminNfcTag?>(null) }
    var activationPhoneTag by remember { mutableStateOf<AdminNfcTag?>(null) }

    val statusFilterLabel = TagStatusLabels.FILTER.firstOrNull { it.first == state.status }?.second ?: "Đang lọc"
    val orderFilterLabel = when (state.orderStatus) {
        "" -> "Tất cả đơn"
        "without_order" -> "Chưa có đơn"
        else -> PancakeOrderStatuses.BY_CODE[state.orderStatus]?.label ?: "Đang lọc"
    }

    val selectedProgrammable = remember(state.selectedTagIds, state.rows) {
        viewModel.getSelectedProgrammableTags()
    }
    val unprintableIds = remember(state.selectedTagIds, state.rows) {
        viewModel.getUnprintableSelectedIds()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (lastToastIsError) NfcColors.DangerText else NfcColors.DarkBarEnd,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        bottomBar = {
            if (state.selectedTagIds.isNotEmpty() && !searchFocused) {
                SelectionActionBar(
                    selectedCount = state.selectedTagIds.size,
                    programmableCount = selectedProgrammable.size,
                    unprintableIds = unprintableIds,
                    getKnownTag = viewModel::getKnownTag,
                    busy = state.busy || state.bulkPancakeSyncing,
                    guidePrinting = state.guidePrinting,
                    showProgramButton = state.showProgramButton,
                    showGuideButton = state.showGuideButton,
                    moreOpen = moreSelectionActionsOpen,
                    deselectAfterProgramming = state.deselectAfterProgramming,
                    deselectAfterPrinting = state.deselectAfterPrinting,
                    onToggleMore = { moreSelectionActionsOpen = !moreSelectionActionsOpen },
                    onClearSelection = viewModel::clearSelection,
                    onProgramSelected = { viewModel.startProgramming(selectedProgrammable) },
                    onEditFeaturesSelected = {
                        if (state.selectedTagIds.size > 200) {
                            viewModel.emitToast("Mỗi lần chỉ cập nhật được tối đa 200 thẻ. Bác bỏ chọn bớt thẻ nhé.", true)
                        } else {
                            featureDialogTagIds = state.selectedTagIds
                            featureDialogInitial = AppFeatures.ALL_KEYS
                        }
                    },
                    onExportSelected = viewModel::exportCsv,
                    onPrintGuides = viewModel::printSelectedGuides,
                    onOpenGuideEditor = { guideEditorOpen = true },
                    onRemoveUnprintable = { viewModel.removeFromSelection(unprintableIds) },
                    onDeselectAfterProgrammingChange = viewModel::setDeselectAfterProgramming,
                    onDeselectAfterPrintingChange = viewModel::setDeselectAfterPrinting,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NfcColors.PearlBackgroundBrush)
                .padding(innerPadding)
                .onGloballyPositioned { coords ->
                    rootOriginInWindow = coords.positionInWindow()
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && searchFocused) {
                                val touchLocal = event.changes.firstOrNull()?.position
                                val bounds = searchBoundsInWindow
                                if (touchLocal != null && bounds != null) {
                                    val touchInWindow = rootOriginInWindow + touchLocal
                                    if (!bounds.contains(touchInWindow)) {
                                        dismissSearchFocus()
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { viewModel.loadTags(background = false) },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Header row (matching web h1)
                    item(key = "header") {
                        Text(
                            text = "Thẻ kích hoạt máy",
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black,
                            color = NfcColors.TextPrimary,
                        )
                    }

                    // Primary actions: Tạo thẻ mới + Thêm việc
                    item(key = "top-actions") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SilverPrimaryButton(
                                onClick = { createBatchOpen = true },
                                enabled = !state.busy && !state.bulkPancakeSyncing,
                                minHeightDp = 56,
                                modifier = Modifier.weight(1.8f),
                            ) {
                                Text("+ Tạo thẻ mới", fontSize = 16.5.sp, fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
                            }
                            LineActionButton(
                                onClick = { toolsOpen = true },
                                minHeightDp = 56,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Thêm việc", fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // Search box + circular ClearSearchButton + scope toggle (exact web match)
                    item(key = "search") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val searchShape = RoundedCornerShape(18.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .onGloballyPositioned { coords ->
                                        searchBoundsInWindow = coords.boundsInWindow()
                                    }
                                    .shadow(4.dp, searchShape, ambientColor = Color(0x22334155), spotColor = Color(0x22334155))
                                    .clip(searchShape)
                                    .background(Color.White.copy(alpha = 0.96f))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (searchFocused) Color(0xFFCBD5E1) else Color.White,
                                        shape = searchShape,
                                    )
                                    .padding(start = 15.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SearchVectorIcon(color = Color(0xFF7B93AB), size = 21.dp)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (state.search.isEmpty()) {
                                        Text(
                                            text = "Tìm tên khách, số điện thoại",
                                            fontSize = 16.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF8A99AB),
                                        )
                                    }
                                    BasicTextField(
                                        value = state.search,
                                        onValueChange = viewModel::updateSearch,
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NfcColors.TextPrimary,
                                        ),
                                        cursorBrush = SolidColor(NfcColors.TextPrimary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = { dismissSearchFocus() },
                                            onDone = { dismissSearchFocus() },
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchFocusRequester)
                                            .onFocusChanged { searchFocused = it.isFocused },
                                    )
                                }
                                if (state.search.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.08f))
                                            .clickable { viewModel.updateSearch("") },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CloseVectorIcon(color = Color(0xFF334155), size = 18.dp)
                                    }
                                }
                            }
                            if (state.searchActive && (state.status.isNotEmpty() || state.orderStatus.isNotEmpty() || state.attnOnly)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NfcColors.SurfaceMuted)
                                        .clickable { viewModel.toggleSearchScoped() }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (state.searchScoped) "Đang tìm trong thẻ đã lọc" else "Đang tìm trong tất cả thẻ",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NfcColors.TextMuted,
                                    )
                                    Text(
                                        text = if (state.searchScoped) "Tìm tất cả thẻ" else "Chỉ tìm trong thẻ đã lọc",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NfcColors.TextPrimary,
                                    )
                                }
                            }
                        }
                    }

                    // 2 Filter buttons
                    item(key = "filters") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterButton(
                                label = "Tình trạng thẻ",
                                value = statusFilterLabel,
                                active = state.status.isNotEmpty(),
                                onClick = { statusSheetOpen = true },
                                modifier = Modifier.weight(1f),
                            )
                            FilterButton(
                                label = "Đơn mua thẻ",
                                value = orderFilterLabel,
                                active = state.orderStatus.isNotEmpty(),
                                onClick = { orderSheetOpen = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Attention banner
                    if (state.attentionCount > 0) {
                        item(key = "attention") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NfcColors.AmberBg)
                                    .border(1.dp, NfcColors.AmberBorder, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.toggleAttentionOnly() }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("⚠", fontSize = 18.sp, color = NfcColors.AmberText)
                                Text(
                                    text = if (state.effAttnOnly) "Đang chỉ xem thẻ cần xem." else "Có ${state.attentionCount} thẻ cần xem.",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NfcColors.AmberText,
                                )
                            }
                        }
                    }

                    // Active filter summary
                    if (state.hasActiveFilter && !state.loading) {
                        item(key = "filter-summary") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NfcColors.SurfaceMuted)
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Đang lọc, còn ${state.shownRows.size} thẻ",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NfcColors.TextMuted,
                                )
                                TextButton(onClick = viewModel::clearFilters) {
                                    Text("Bỏ lọc", fontWeight = FontWeight.ExtraBold, color = NfcColors.TextPrimary)
                                }
                            }
                        }
                    }

                    // Select all visible
                    if (state.shownRows.isNotEmpty() && !state.loading) {
                        item(key = "select-all") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleAllVisible() }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = state.allVisibleSelected,
                                    onCheckedChange = { viewModel.toggleAllVisible() },
                                    colors = CheckboxDefaults.colors(checkedColor = NfcColors.DarkBarStart),
                                )
                                Text(
                                    text = if (state.allVisibleSelected) "Bỏ chọn hết" else "Chọn hết ${state.shownRows.size} thẻ đang hiện",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NfcColors.TextDeep,
                                )
                            }
                        }
                    }

                    // Empty / Loading / Tag cards
                    if (state.loading && state.rows.isEmpty()) {
                        item(key = "loading") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.White)
                                    .padding(vertical = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                                Text("Đang tải thẻ...", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NfcColors.TextMuted)
                            }
                        }
                    } else if (state.shownRows.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.White)
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Chưa có thẻ nào phù hợp.",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NfcColors.TextMuted,
                                )
                            }
                        }
                    } else {
                        items(items = state.shownRows, key = { it.id }) { tag ->
                            NfcTagCardItem(
                                tag = tag,
                                selected = tag.id in state.selectedTagIds,
                                busy = state.busy || state.bulkPancakeSyncing,
                                copying = state.copyingTagId == tag.id,
                                pancakeCopying = state.pancakeCopyingTagId == tag.id,
                                showProgramButton = state.showProgramButton,
                                onToggle = { viewModel.toggleTagSelection(tag.id) },
                                onOpen = { viewModel.openDetail(tag.id) },
                                onCopyCode = { viewModel.copyActivationCode(tag) },
                                onCopyLink = { viewModel.copyTagLink(tag) },
                                onCopyMessage = { viewModel.copyPancakeMessage(tag) },
                                onProgram = { viewModel.startProgramming(listOf(tag)) },
                            )
                        }
                    }

                    item(key = "bottom-spacer") {
                        Spacer(Modifier.height(72.dp))
                    }
                }
            }

            // Floating "Tìm và lọc" button when scrolled past search box (exact ScrollToSearchButton parity)
            if (showScrollToSearch && !searchFocused) {
                val pillShape = RoundedCornerShape(50)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 14.dp)
                        .navigationBarsPadding()
                        .shadow(10.dp, pillShape, ambientColor = Color(0x5500143C), spotColor = Color(0x5500143C))
                        .clip(pillShape)
                        .background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.08f), pillShape)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(2)
                                delay(220L)
                                runCatching { searchFocusRequester.requestFocus() }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchVectorIcon(color = NfcColors.TextDeep, size = 19.dp)
                    Text(
                        text = "Tìm và lọc",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NfcColors.TextDeep,
                    )
                }
            }
        }
    }

    // ==================== DIALOGS & SHEETS ====================

    if (state.programmingQueue.isNotEmpty()) {
        NfcProgrammingDialog(
            queue = state.programmingQueue,
            api = viewModel.api,
            onTagVerified = viewModel::onTagProgrammedAndVerified,
            onSendLog = viewModel::sendProgrammingLog,
            onClose = viewModel::closeProgramming,
        )
    }

    val detailTag = state.detailTagId?.let { viewModel.getKnownTag(it) }
    if (detailTag != null) {
        NfcTagDetailSheet(
            tag = detailTag,
            extra = state.detailExtra.takeIf { it.tagId == detailTag.id },
            busy = state.busy || state.bulkPancakeSyncing,
            copying = state.copyingTagId == detailTag.id,
            pancakeCopying = state.pancakeCopyingTagId == detailTag.id,
            showProgramButton = state.showProgramButton,
            onDismiss = viewModel::closeDetail,
            onProgram = { viewModel.startProgramming(listOf(detailTag)) },
            onAddPhone = {
                viewModel.closeDetail()
                activationPhoneTag = detailTag
            },
            onMachine = { viewModel.openMachineProfile(detailTag) },
            onEnable = { viewModel.enableTag(detailTag) },
            onDisable = {
                viewModel.closeDetail()
                disableTag = detailTag
            },
            onCopyCode = { viewModel.copyActivationCode(detailTag) },
            onCopyLink = { viewModel.copyTagLink(detailTag) },
            onCopyMessage = { viewModel.copyPancakeMessage(detailTag) },
            onEditFeatures = {
                viewModel.closeDetail()
                featureDialogTagIds = listOf(detailTag.id)
                featureDialogInitial = detailTag.features.ifEmpty { AppFeatures.ALL_KEYS }
            },
            onAttachOrder = {
                viewModel.closeDetail()
                attachOrderTag = detailTag
            },
            onTransfer = {
                viewModel.closeDetail()
                transferTag = detailTag
            },
            onSeparateAccount = {
                viewModel.closeDetail()
                separateTag = detailTag
            },
            onAttachLegacy = {
                viewModel.closeDetail()
                legacyTag = detailTag
            },
        )
    }

    if (statusSheetOpen) {
        StatusFilterSheet(
            current = state.status,
            onSelect = viewModel::updateStatusFilter,
            onDismiss = { statusSheetOpen = false },
        )
    }

    if (orderSheetOpen) {
        OrderFilterSheet(
            current = state.orderStatus,
            onSelect = viewModel::updateOrderStatusFilter,
            onDismiss = { orderSheetOpen = false },
        )
    }

    if (toolsOpen) {
        ToolsSheet(
            busy = state.busy,
            bulkPancakeSyncing = state.bulkPancakeSyncing,
            bulkPancakeProgress = state.bulkPancakeProgress,
            selectedCount = state.selectedTagIds.size,
            defaultFeatures = state.defaultFeatures,
            defaultFeaturesLoading = state.defaultFeaturesLoading,
            onCopyTrialLink = viewModel::copyLandMeasureTrialLink,
            onSyncAllPancake = viewModel::syncAllPancake,
            onCreateFullAccessCodes = viewModel::createAndShareFullAccessCodes,
            onExportCsv = viewModel::exportCsv,
            onOpenGuideEditor = { guideEditorOpen = true },
            onOpenButtonSettings = { buttonSettingsOpen = true },
            onOpenDefaultFeatures = { defaultsOpen = true },
            onLogout = viewModel::logout,
            onDismiss = { toolsOpen = false },
        )
    }

    if (buttonSettingsOpen) {
        ButtonSettingsSheet(
            showProgramButton = state.showProgramButton,
            showGuideButton = state.showGuideButton,
            onToggleProgram = viewModel::setShowProgramButton,
            onToggleGuide = viewModel::setShowGuideButton,
            onDismiss = { buttonSettingsOpen = false },
        )
    }

    if (defaultsOpen) {
        FeaturesEditorSheet(
            title = "Quyền mặc định cho thẻ mới",
            description = "Thẻ tạo mới sẽ dùng các quyền này. Khi tạo đơn, bác có thể chọn quyền riêng cho thẻ trong đơn đó.",
            initialFeatures = state.defaultFeatures,
            busy = state.busy || state.defaultFeaturesLoading,
            onSave = { features ->
                viewModel.saveDefaultFeatures(features) { defaultsOpen = false }
            },
            onDismiss = { defaultsOpen = false },
        )
    }

    if (featureDialogTagIds.isNotEmpty()) {
        val count = featureDialogTagIds.size
        FeaturesEditorSheet(
            title = if (count == 1) "Sửa quyền cho thẻ" else "Sửa quyền cho $count thẻ",
            description = "Các quyền được chọn sẽ thay thế quyền hiện tại${if (count == 1) " của thẻ này." else " của tất cả thẻ đã chọn."}",
            initialFeatures = featureDialogInitial,
            busy = state.busy,
            onSave = { features ->
                viewModel.updateTagFeatures(featureDialogTagIds, features) {
                    featureDialogTagIds = emptyList()
                }
            },
            onDismiss = { featureDialogTagIds = emptyList() },
        )
    }

    if (createBatchOpen) {
        CreateBatchSheet(
            busy = state.busy,
            onCreate = { qty ->
                createBatchOpen = false
                viewModel.createBatch(qty)
            },
            onDismiss = { createBatchOpen = false },
        )
    }

    attachOrderTag?.let { tag ->
        SingleInputActionSheet(
            title = "Gắn thẻ vào đơn mua",
            description = "Thẻ ${tag.codeLabel} sẽ lấy tên và số điện thoại từ đơn này để khách kích hoạt máy.",
            fieldLabel = "Mã đơn mua thẻ",
            placeholder = "Ví dụ: 345",
            helperText = "Nhập mã đơn đang hiển thị trong phần Đơn hàng. Vẫn có thể gắn thêm khi đơn đã đủ số thẻ đã mua.",
            submitLabel = "Gắn",
            keyboardType = KeyboardType.Number,
            busy = state.busy,
            onSubmit = { ref -> viewModel.attachOrder(tag, ref) { attachOrderTag = null } },
            onDismiss = { attachOrderTag = null },
        )
    }

    transferTag?.let { tag ->
        SingleInputActionSheet(
            title = "Chuyển sang tài khoản khác",
            description = "Dùng khi số điện thoại trên đơn bị gõ sai. Bác nhập số điện thoại của tài khoản nhận ${tag.codeLabel}.",
            warningBox = "Sau khi chuyển, mã và đường dẫn cũ không dùng được. Bác cần chép mã, đường dẫn mới rồi ghi lại thẻ.",
            fieldLabel = "Số điện thoại nhận thẻ",
            placeholder = "Ví dụ: 0912 345 678",
            submitLabel = "Chuyển",
            keyboardType = KeyboardType.Phone,
            busy = state.busy,
            onSubmit = { phone -> viewModel.transferTag(tag, phone) { transferTag = null } },
            onDismiss = { transferTag = null },
        )
    }

    legacyTag?.let { tag ->
        SingleInputActionSheet(
            title = "Gắn vào máy cũ",
            description = "Bác nhập mã của máy cũ cần gắn ${tag.codeLabel}.",
            fieldLabel = "Mã máy cũ",
            placeholder = "Ví dụ: 1024",
            submitLabel = "Gắn",
            keyboardType = KeyboardType.Number,
            busy = state.busy,
            onSubmit = { id -> viewModel.attachLegacyMachine(tag, id) { legacyTag = null } },
            onDismiss = { legacyTag = null },
        )
    }

    separateTag?.let { tag ->
        SeparateAccountConfirmSheet(
            tag = tag,
            busy = state.busy,
            onConfirm = { viewModel.separateTagAccount(tag) { separateTag = null } },
            onDismiss = { separateTag = null },
        )
    }

    disableTag?.let { tag ->
        DisableTagConfirmSheet(
            tag = tag,
            busy = state.busy,
            onConfirm = { viewModel.disableTag(tag) { disableTag = null } },
            onDismiss = { disableTag = null },
        )
    }

    activationPhoneTag?.let { tag ->
        SingleInputActionSheet(
            title = "Thêm số điện thoại",
            description = "Nhập số điện thoại của khách để tạo mã kích hoạt cho ${tag.codeLabel}. Nếu thẻ đã gắn máy, số này sẽ được thêm vào tài khoản chủ máy khi tài khoản chưa có số.",
            fieldLabel = "Số điện thoại của khách",
            placeholder = "Ví dụ: 0912 345 678",
            helperText = "Sau khi lưu, mã kích hoạt sẽ được tạo và tự sao chép để gửi cho khách.",
            submitLabel = "Lưu",
            keyboardType = KeyboardType.Phone,
            busy = state.busy,
            onSubmit = { phone -> viewModel.addActivationPhone(tag, phone) { activationPhoneTag = null } },
            onDismiss = { activationPhoneTag = null },
        )
    }

    if (guideEditorOpen) {
        NfcGuideEditorSheet(
            api = viewModel.api,
            onToast = viewModel::emitToast,
            onDismiss = { guideEditorOpen = false },
        )
    }

    state.machineProfileTag?.let { tag ->
        NfcMachineProfileSheet(
            tag = tag,
            api = viewModel.api,
            onSaved = {
                viewModel.emitToast("Đã lưu thông tin máy.")
                viewModel.loadTags(background = false)
            },
            onDismiss = viewModel::closeMachineProfile,
        )
    }
}

@Composable
private fun FilterButton(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (active) Color(0xFFFFFDF7) else Color.White
    val border = if (active) Color(0xFFD8C9A8) else Color.White
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NfcColors.TextMuted)
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = NfcColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("▾", fontSize = 16.sp, color = NfcColors.TextMuted)
    }
}

@Composable
private fun NfcTagCardItem(
    tag: AdminNfcTag,
    selected: Boolean,
    busy: Boolean,
    copying: Boolean,
    pancakeCopying: Boolean,
    showProgramButton: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onCopyCode: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyMessage: () -> Unit,
    onProgram: () -> Unit,
) {
    val (dotColor, statusTextColor) = when (tag.status) {
        "active" -> NfcColors.GreenActive to NfcColors.GreenActiveText
        "unactivated" -> Color(0xFF64748B) to Color(0xFF475569)
        else -> Color(0xFF94A3B8) to Color(0xFF475569)
    }
    val subLine = if (tag.status == "active") {
        tag.machineLabel?.takeIf { it.isNotBlank() } ?: "Đã gắn máy"
    } else {
        tag.effectivePhone.ifBlank { if (tag.status == "replaced") "" else "Chưa có số điện thoại" }
    }

    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color(0x22334155), spotColor = Color(0x22334155))
            .clip(shape)
            .background(if (selected) NfcColors.SelectedBg else Color.White)
            .border(
                width = if (selected) 2.5.dp else 1.5.dp,
                color = if (selected) NfcColors.SelectedBorder else NfcColors.CardBorder,
                shape = shape,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = NfcColors.DarkBarStart),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen),
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                color = NfcColors.TextDeep,
                            ),
                        ) {
                            append(tag.codeLabel)
                        }
                        if (!tag.orderRecipientName.isNullOrBlank()) {
                            append("  ")
                            withStyle(
                                SpanStyle(
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                ),
                            ) {
                                append(tag.orderRecipientName)
                            }
                        }
                    },
                    lineHeight = 23.sp,
                )
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = TagStatusLabels.cardLabel(tag.status),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor,
                    )
                }
                if (subLine.isNotEmpty()) {
                    Text(
                        text = subLine,
                        fontSize = 14.5.sp,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (tag.needsAttention) {
                    Text(
                        text = if (!tag.activationStartedAt.isNullOrBlank()) {
                            "Khách đang kích hoạt thẻ này"
                        } else {
                            "Tin nhắn cho khách chưa gửi được"
                        },
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NfcColors.AmberText,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Text(
                text = "›",
                fontSize = 28.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 4.dp),
            )
        }

        if (!tag.isOff) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        CopyVectorIcon(color = NfcColors.BlueText, size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(if (copying) "Đang chép" else "Chép mã", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
                    LinkVectorIcon(color = NfcColors.EmeraldText, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(if (copying) "Đang chép" else "Chép link", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
                        MessageVectorIcon(color = NfcColors.AmberText, size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(if (pancakeCopying) "Đang chép" else "Tin nhắn", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
            }
        }

        if (showProgramButton) {
            if (tag.canProgram) {
                SilverPrimaryButton(
                    onClick = onProgram,
                    enabled = !busy,
                    minHeightDp = 52,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NfcWaveVectorIcon(color = NfcColors.SilverBtnText, size = 21.dp)
                    Text("Nạp vào thẻ", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NfcColors.SilverBtnText)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NfcColors.SurfaceSoft)
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (tag.status == "revoked") "Thẻ đã tắt nên chưa nạp được" else "Thẻ đã thay nên không nạp nữa",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NfcColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    programmableCount: Int,
    unprintableIds: List<Long>,
    getKnownTag: (Long) -> AdminNfcTag?,
    busy: Boolean,
    guidePrinting: Boolean,
    showProgramButton: Boolean,
    showGuideButton: Boolean,
    moreOpen: Boolean,
    deselectAfterProgramming: Boolean,
    deselectAfterPrinting: Boolean,
    onToggleMore: () -> Unit,
    onClearSelection: () -> Unit,
    onProgramSelected: () -> Unit,
    onEditFeaturesSelected: () -> Unit,
    onExportSelected: () -> Unit,
    onPrintGuides: () -> Unit,
    onOpenGuideEditor: () -> Unit,
    onRemoveUnprintable: () -> Unit,
    onDeselectAfterProgrammingChange: (Boolean) -> Unit,
    onDeselectAfterPrintingChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(Brush.verticalGradient(listOf(NfcColors.DarkBarStart, NfcColors.DarkBarEnd)))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Gold status banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NfcColors.GoldBannerBrush)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NfcColors.DarkBarEnd),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = selectedCount.toString(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = NfcColors.GoldStart,
                )
            }
            Text(
                text = "Bác đang chọn $selectedCount thẻ",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Black,
                color = NfcColors.GoldText,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(NfcColors.DarkBarEnd)
                    .clickable(onClick = onClearSelection)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                CloseVectorIcon(color = Color.White, size = 15.dp)
                Text("Bỏ chọn hết", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

        // Main row: Nạp N thẻ + Việc khác
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showProgramButton) {
                SilverPrimaryButton(
                    onClick = onProgramSelected,
                    enabled = programmableCount > 0,
                    minHeightDp = 52,
                    modifier = Modifier.weight(1f),
                ) {
                    NfcWaveVectorIcon(color = NfcColors.SilverBtnText, size = 21.dp)
                    Text(
                        text = "Nạp $programmableCount thẻ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = NfcColors.SilverBtnText,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onToggleMore)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Việc khác ${if (moreOpen) "▴" else "▾"}",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
        }

        if (moreOpen) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkOutlinedBarButton(
                    text = "Sửa quyền $selectedCount thẻ",
                    enabled = !busy,
                    onClick = onEditFeaturesSelected,
                    modifier = Modifier.weight(1f),
                )
                DarkOutlinedBarButton(
                    text = "Tải file $selectedCount thẻ",
                    enabled = !busy,
                    onClick = onExportSelected,
                    modifier = Modifier.weight(1f),
                )
            }

            if (showGuideButton) {
                DarkOutlinedBarButton(
                    text = if (guidePrinting) "Đang chuẩn bị tờ hướng dẫn..." else "In $programmableCount hướng dẫn",
                    enabled = !busy && !guidePrinting,
                    onClick = onPrintGuides,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Sửa nội dung tờ hướng dẫn",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE5E7EB),
                    modifier = Modifier
                        .clickable(onClick = onOpenGuideEditor)
                        .padding(vertical = 4.dp),
                )
                if (unprintableIds.isNotEmpty()) {
                    val names = unprintableIds.joinToString(", ") { id ->
                        val t = getKnownTag(id)
                        if (t != null) "${t.codeLabel} (${TagStatusLabels.FULL[t.status] ?: "không in được"})" else "1 thẻ bị ẩn"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF0D0))
                            .padding(10.dp),
                    ) {
                        Text(
                            text = "Không in được ${unprintableIds.size} thẻ đã chọn: $names.",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8A5200),
                        )
                        Text(
                            text = "Bỏ chọn ${if (unprintableIds.size == 1) "thẻ này" else "các thẻ này"}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF8A5200),
                            modifier = Modifier
                                .clickable(onClick = onRemoveUnprintable)
                                .padding(top = 4.dp),
                        )
                    }
                }
                if (programmableCount > NfcGuideContent.MAX_CARDS) {
                    Text(
                        text = "Mỗi lần chỉ in được tối đa ${NfcGuideContent.MAX_CARDS} hướng dẫn. Bác bỏ chọn bớt thẻ nhé.",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8A5200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF0D0))
                            .padding(10.dp),
                    )
                }
            }

            if (showProgramButton) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeselectAfterProgrammingChange(!deselectAfterProgramming) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = deselectAfterProgramming,
                        onCheckedChange = onDeselectAfterProgrammingChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF9CA3AF),
                            uncheckedColor = Color(0xFFD1D5DB),
                        ),
                    )
                    Text("Nạp xong tự bỏ chọn thẻ đó", fontSize = 14.5.sp, color = Color(0xFFD1D5DB))
                }
            }
            if (showGuideButton) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeselectAfterPrintingChange(!deselectAfterPrinting) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = deselectAfterPrinting,
                        onCheckedChange = onDeselectAfterPrintingChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF9CA3AF),
                            uncheckedColor = Color(0xFFD1D5DB),
                        ),
                    )
                    Text("In xong tự bỏ chọn thẻ đó", fontSize = 14.5.sp, color = Color(0xFFD1D5DB))
                }
            }
        }
    }
}

@Composable
private fun DarkOutlinedBarButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.1f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
