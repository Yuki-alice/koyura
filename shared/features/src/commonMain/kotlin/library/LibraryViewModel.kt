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
import kotlinx.coroutines.launch

/**
 * 书架 MVVM 样板（阶段 0）。
 *
 * 容器：androidx lifecycle ViewModel（KMP）+ StateFlow + viewModelScope。
 * 状态：不可变 [LibraryUiState]（UDF）；副作用走一次性 [LibraryEffect]。
 * 导航域化：经 `lifecycle-viewmodel-navigation3` 按 NavKey 持有，
 * 创建走 Koin `viewModel { params -> ... }` + `koinViewModel(parametersOf(...))`。
 *
 * 实现策略：内部持有既有 [LibrarySession]（全部并发/收敛规则已验证），
 * ViewModel 只做生命周期 + UDF 门面，不复制业务逻辑。
 * UI 可先用 `viewModel.session` 渐进迁移，再切到 `uiState` 渲染。
 */
data class LibraryUiState(
    val loadState: LibraryLoadState = LibraryLoadState.Loading,
    val snapshot: LibrarySnapshot = LibrarySnapshot(),
    val query: String = "",
    val activeCategoryId: Long? = null,
    val selection: Set<LibraryEntryKey> = emptySet(),
    val actionError: String? = null,
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

/** 书架一次性副作用：提示/导航类事件。 */
sealed interface LibraryEffect {
    data class Message(val text: String) : LibraryEffect
}

class LibraryViewModel(
    repository: LibraryRepository?,
    downloads: DownloadQueueRepository? = null,
) : ViewModel() {
    /** 既有会话：唯一真相源，全部落盘/并发规则不变。 */
    val session: LibrarySession = LibrarySession(repository, downloads)

    private val _uiState = MutableStateFlow(capture())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<LibraryEffect>(extraBufferCapacity = 1)
    val effect: SharedFlow<LibraryEffect> = _effect.asSharedFlow()

    init {
        viewModelScope.launch {
            snapshotFlow { capture() }
                .distinctUntilChanged()
                .collect { _uiState.value = it }
        }
    }

    private fun capture(): LibraryUiState = LibraryUiState(
        loadState = session.loadState,
        snapshot = session.snapshot,
        query = session.query,
        activeCategoryId = session.activeCategoryId,
        selection = session.selection,
        actionError = session.actionError,
    )

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
