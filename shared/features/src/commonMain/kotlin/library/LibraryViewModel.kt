package app.koyura.features.library

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.koyura.features.downloads.DownloadQueueRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 书架 MVVM 样板（阶段 0）。
 *
 * 容器：androidx lifecycle ViewModel（KMP）+ StateFlow + viewModelScope。
 * 状态：不可变 [LibraryUiState]（UDF）；副作用走一次性 [LibraryEffect]。
 * 域化：现跟随宿主 ViewModelStoreOwner（书架单根与之一致）；
 * `lifecycle-viewmodel-navigation3` 按 NavKey 域化待多 VM 条目时再接
 * （其装饰器会丢被盖条目的 rememberSaveState经 VeneraBrowseFlowTest 实证回退）。
 *
 * 实现策略：包裹调用方传入的既有 [LibrarySession]（单实例不分叉，
 * 全部并发/收敛规则已验证），ViewModel 只做生命周期 + UDF 门面，不复制业务逻辑。
 * UI 只读 [uiState] 渲染；无 Koin 的测试/预览走 [LibrarySession.asUiStateFlow] 同一快照。
 */
data class LibraryUiState(
    val loadState: LibraryLoadState = LibraryLoadState.Loading,
    val snapshot: LibrarySnapshot = LibrarySnapshot(),
    val query: String = "",
    val activeCategoryId: Long? = null,
    val selection: Set<LibraryEntryKey> = emptySet(),
    val actionError: String? = null,
    val tabs: List<LibraryCategory?> = listOf(null),
    val counts: Map<Long?, Int> = emptyMap(),
    val visibleSections: List<LibrarySection> = emptyList(),
    val hasDownloads: Boolean = false,
) {
    /** 当前视图（隐藏 + 搜索 + 分类 + 筛选 + 排序已应用）。 */
    val visibleEntries: List<LibraryEntry> get() = applyLibraryView(
        entries = snapshot.entries.let { entries ->
            if (snapshot.prefs.showHiddenCategories) entries
            else {
                val hiddenIds = snapshot.categories.filter { it.isHidden }.map { it.id }.toSet()
                if (hiddenIds.isEmpty()) entries
                else entries.filter { entry ->
                    val ids = snapshot.links[entry.key] ?: emptySet()
                    ids.isEmpty() || ids.any { it !in hiddenIds }
                }
            }
        },
        links = snapshot.links,
        query = query,
        categoryId = activeCategoryId,
        filters = snapshot.prefs.filters,
        sortType = snapshot.prefs.sortType,
        ascending = snapshot.prefs.ascending,
        randomSeed = snapshot.prefs.randomSeed,
    )
    val wantsBack: Boolean get() = selection.isNotEmpty() || query.isNotEmpty()
}

/** 会话快照 → 不可变 UI 态（ViewModel 与测试适配器共用同一实现）。 */
internal fun LibrarySession.captureUiState(): LibraryUiState {
    val tabs = this.tabs
    return LibraryUiState(
        loadState = loadState,
        snapshot = snapshot,
        query = query,
        activeCategoryId = activeCategoryId,
        selection = selection,
        actionError = actionError,
        tabs = tabs,
        counts = tabs.associate { category -> (category?.id) to countForTab(category?.id) },
        visibleSections = groupLibraryEntries(
            applyLibraryView(
                entries = snapshot.entries.let { entries ->
                    if (snapshot.prefs.showHiddenCategories) entries
                    else {
                        val hiddenIds = snapshot.categories.filter { it.isHidden }.map { it.id }.toSet()
                        if (hiddenIds.isEmpty()) entries
                        else entries.filter { entry ->
                            val ids = snapshot.links[entry.key] ?: emptySet()
                            ids.isEmpty() || ids.any { it !in hiddenIds }
                        }
                    }
                },
                links = snapshot.links,
                query = query,
                categoryId = activeCategoryId,
                filters = snapshot.prefs.filters,
                sortType = snapshot.prefs.sortType,
                ascending = snapshot.prefs.ascending,
                randomSeed = snapshot.prefs.randomSeed,
            ),
            snapshot.prefs.groupBy,
        ),
        hasDownloads = downloads != null,
    )
}

/**
 * 会话 → UiState 流（无 Koin 的测试/预览用，与 [LibraryViewModel.uiState] 同快照；
 * 生产走 ViewModel 持有域化的那一份）。
 */
fun LibrarySession.asUiStateFlow(scope: kotlinx.coroutines.CoroutineScope): StateFlow<LibraryUiState> {
    val session = this
    return snapshotFlow { session.captureUiState() }
        .distinctUntilChanged()
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, session.captureUiState())
}

/** 书架一次性副作用：提示/导航类事件。 */
sealed interface LibraryEffect {
    data class Message(val text: String) : LibraryEffect
}

class LibraryViewModel(
    val session: LibrarySession,
) : ViewModel() {
    /** Koin/直构兼容入口：自建会话（与包入既有会话同一份逻辑）。 */
    constructor(
        repository: LibraryRepository?,
        downloads: DownloadQueueRepository? = null,
    ) : this(LibrarySession(repository, downloads))

    private val _uiState = MutableStateFlow(session.captureUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<LibraryEffect>(extraBufferCapacity = 1)
    val effect: SharedFlow<LibraryEffect> = _effect.asSharedFlow()

    init {
        viewModelScope.launch {
            snapshotFlow { session.captureUiState() }
                .distinctUntilChanged()
                .collect { _uiState.value = it }
        }
    }

    private fun capture(): LibraryUiState = session.captureUiState()

    fun refresh() {
        viewModelScope.launch {
            session.refresh()
            _uiState.value = capture()
        }
    }

    fun updateQuery(value: String) {
        session.updateQuery(value)
        _uiState.value = capture()
    }

    fun selectCategory(id: Long?) {
        session.selectCategory(id)
        _uiState.value = capture()
    }

    fun toggleSelect(key: LibraryEntryKey) {
        session.toggleSelect(key)
        _uiState.value = capture()
    }

    fun clearSelection() {
        session.clearSelection()
        _uiState.value = capture()
    }

    fun selectAllVisible() {
        session.selectAllVisible()
        _uiState.value = capture()
    }

    fun invertSelection() {
        session.invertSelection()
        _uiState.value = capture()
    }

    fun consumeBack(): LibraryBackOutcome {
        val outcome = session.consumeBack()
        _uiState.value = capture()
        return outcome
    }

    fun clearActionError() {
        session.clearActionError()
        _uiState.value = capture()
    }

    fun noteError(message: String) {
        session.noteError(message)
        _uiState.value = capture()
        _effect.tryEmit(LibraryEffect.Message(message))
    }

    suspend fun removeSelected(): Boolean {
        val ok = session.removeSelected()
        _uiState.value = capture()
        return ok
    }

    suspend fun restore(
        entries: List<LibraryEntry>,
        categories: Map<LibraryEntryKey, Set<Long>>,
    ): Boolean {
        val ok = session.restore(entries, categories)
        _uiState.value = capture()
        return ok
    }

    suspend fun setCategoriesForSelected(categoryIds: Set<Long>): Boolean {
        val ok = session.setCategoriesForSelected(categoryIds)
        _uiState.value = capture()
        return ok
    }

    suspend fun markSelectedAsRead(read: Boolean): Boolean {
        val ok = session.markSelectedAsRead(read)
        _uiState.value = capture()
        return ok
    }

    suspend fun downloadSelected(): Boolean {
        val ok = session.downloadSelected()
        _uiState.value = capture()
        if (!ok) session.actionError?.let { _effect.tryEmit(LibraryEffect.Message(it)) }
        return ok
    }

    suspend fun updatePrefs(transform: (LibraryPrefs) -> LibraryPrefs): Boolean {
        val ok = session.updatePrefs(transform)
        _uiState.value = capture()
        return ok
    }

    suspend fun ensureCover(entry: LibraryEntry) = session.ensureCover(entry)
    suspend fun prefetchCovers(entries: List<LibraryEntry>) = session.prefetchCovers(entries)
    suspend fun retryCover(entry: LibraryEntry) = session.retryCover(entry)
}
