package app.koyura.features.reader.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.domain.reader.preferences.ReaderPreferences
import app.koyura.domain.reader.preferences.ReaderPreferencesSnapshot
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.domain.reader.preferences.patchedWith
import app.koyura.features.reader.comic.chrome.ChapterEntry
import app.koyura.features.reader.comic.chrome.ReaderChrome
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeDialog
import app.koyura.features.reader.comic.chrome.ReaderChromeState
import app.koyura.features.reader.comic.viewer.ComicChapterBoundary
import app.koyura.features.reader.comic.viewer.ComicPagerLayout
import app.koyura.features.reader.comic.viewer.ComicReadingMode
import app.koyura.features.reader.comic.viewer.ComicScaleFit
import app.koyura.features.reader.comic.viewer.ComicViewer
import app.koyura.features.reader.comic.viewer.ComicViewerJump
import app.koyura.features.reader.comic.viewer.ComicViewerOptions
import app.koyura.features.resources.Res
import app.koyura.features.resources.reader_empty_pages
import app.koyura.features.resources.reader_load_failed
import app.koyura.features.resources.reader_preferences_save_failed
import app.koyura.features.resources.reader_progress_save_failed
import app.koyura.features.resources.reader_retry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Stable UI test hooks, shared with the app production-chain tests. */
const val ReaderRootTestTag = "reader-root"
const val ReaderBackTag = "reader-back"
const val ReaderPageIndicatorTag = "reader-page-indicator"
const val ReaderPrevChapterTag = "reader-prev-chapter"
const val ReaderNextChapterTag = "reader-next-chapter"
const val ReaderTapPrevTag = "reader-tap-prev"
const val ReaderTapNextTag = "reader-tap-next"
const val ReaderTapMenuTag = "reader-tap-menu"

fun readerPageTag(index: Int) = "reader-page-$index"
fun readerPageRetryTag(pageKey: String) = "reader-page-retry-$pageKey"

@Composable
fun ComicReaderScreen(
    session: ReaderSession,
    sourceKey: String,
    workId: String,
    chapterId: String?,
    title: String,
    chapterIds: List<String>,
    chapterTitles: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: ReaderPreferences? = null,
    installBackHandler: @Composable (() -> Unit) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val chapterPicker = remember(chapterIds, chapterTitles) {
        chapterIds.mapIndexed { index, id ->
            ChapterEntry(id, chapterTitles.getOrNull(index)?.takeIf(String::isNotBlank) ?: id)
        }
    }
    var activeChapterId by rememberSaveable(sourceKey, workId, chapterId) { mutableStateOf(chapterId) }
    var menuVisible by rememberSaveable(sourceKey, workId, chapterId) { mutableStateOf(true) }
    var dialog by remember(sourceKey, workId, chapterId) { mutableStateOf(ReaderChromeDialog.None) }
    var jump by remember(session, sourceKey, workId, chapterId) { mutableStateOf<ComicViewerJump?>(null) }
    var requestId by remember(session) { mutableLongStateOf(0L) }
    var attempt by remember { mutableIntStateOf(0) }
    var chapterOpened by remember(session, sourceKey, workId, activeChapterId, attempt) { mutableStateOf(false) }
    var settings by remember(preferences, sourceKey, workId) { mutableStateOf<ReaderPreferencesSnapshot?>(null) }
    var settingsFailed by remember(preferences, sourceKey, workId) { mutableStateOf(false) }
    var openingFailed by remember(preferences, sourceKey, workId, attempt) { mutableStateOf<String?>(null) }
    var changingRoute by remember(session, sourceKey, workId, chapterId) { mutableStateOf(false) }
    var progressFailed by remember(session, sourceKey, workId, chapterId) { mutableStateOf(false) }
    val settingWrites = remember(preferences, sourceKey, workId) { Mutex() }

    LaunchedEffect(preferences, sourceKey, workId, attempt) {
        if (settings == null) {
            try {
                settings = preferences?.load(sourceKey, workId)
                    ?: ReaderPreferencesSnapshot(global = ReaderSettings(layout = ReaderLayout.SINGLE_PAGE))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                openingFailed = failure.message.orEmpty()
            }
        }
    }

    LaunchedEffect(session, sourceKey, workId, activeChapterId, attempt) {
        session.open(sourceKey, workId, activeChapterId, chapterIds, title, chapterTitles)
        chapterOpened = true
    }

    fun changeSettings(action: ReaderChromeAction) {
        scope.launch {
            settingWrites.withLock {
                val current = settings ?: return@withLock
                try {
                    val updated = withContext(NonCancellable) {
                        when (action) {
                            is ReaderChromeAction.ChangeGlobalSettings -> {
                                preferences?.writeGlobal(action.settings)
                                current.copy(global = action.settings)
                            }
                            is ReaderChromeAction.ChangeWorkOverride -> {
                                preferences?.writeOverride(sourceKey, workId, action.patch)
                                val override = (current.override ?: WorkReaderOverride()).patchedWith(action.patch)
                                current.copy(override = override.takeUnless { it.isEmpty() })
                            }
                            ReaderChromeAction.ResetWorkOverride -> {
                                preferences?.clearOverride(sourceKey, workId)
                                current.copy(override = null)
                            }
                            else -> current
                        }
                    }
                    settings = updated
                    settingsFailed = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    settingsFailed = true
                }
            }
        }
    }

    fun afterSavingPage(proceed: () -> Unit) {
        if (changingRoute) return
        changingRoute = true
        scope.launch {
            try {
                session.saveCurrent()
                progressFailed = false
                proceed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                progressFailed = true
            } finally {
                changingRoute = false
            }
        }
    }

    fun goBack() = afterSavingPage(onBack)

    fun openChapter(nextId: String?) {
        if (nextId == activeChapterId) return
        afterSavingPage {
            dialog = ReaderChromeDialog.None
            jump = null
            activeChapterId = nextId
        }
    }

    installBackHandler {
        if (dialog != ReaderChromeDialog.None) dialog = ReaderChromeDialog.None else goBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(ReaderRootTestTag),
    ) {
        val currentSettings = settings
        val state = if (chapterOpened) session.content else ReaderContentState.Loading
        val loadError = openingFailed ?: (state as? ReaderContentState.Failed)?.message
        when {
            loadError != null -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.reader_load_failed, loadError),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { attempt++ }) { Text(stringResource(Res.string.reader_retry)) }
            }
            currentSettings != null && state is ReaderContentState.Ready -> {
                val content = state.content
                val previous = session.previousChapterId(content.chapterId)
                val next = session.nextChapterId(content.chapterId)
                val chrome = ReaderChromeState(
                    workTitle = title.ifEmpty { content.title },
                    chapterId = content.chapterId,
                    chapters = chapterPicker,
                    pageIndex = session.currentPage,
                    pageCount = content.pageKeys.size,
                    menuVisible = menuVisible,
                    hasPrevChapter = previous != null,
                    hasNextChapter = next != null,
                    global = currentSettings.global,
                    override = currentSettings.override,
                    dialog = dialog,
                )
                if (content.pageKeys.isEmpty()) {
                    Text(stringResource(Res.string.reader_empty_pages), Modifier.align(Alignment.Center))
                } else {
                    key(session.contentSeq) {
                        ComicViewer(
                            session = session,
                            content = content,
                            options = chrome.effective.viewerOptions(),
                            jump = jump,
                            onToggleMenu = { menuVisible = !menuVisible },
                            onChapterBoundary = { direction ->
                                val neighbour = if (direction == ComicChapterBoundary.NEXT) next else previous
                                neighbour?.let(::openChapter)
                            },
                            menuVisible = menuVisible,
                            onHideMenu = { menuVisible = false },
                        )
                    }
                }
                ReaderChrome(chrome, onAction = { action ->
                    when (action) {
                        ReaderChromeAction.Back -> goBack()
                        is ReaderChromeAction.SetMenuVisible -> menuVisible = action.visible
                        is ReaderChromeAction.OpenChapter -> openChapter(action.chapterId)
                        is ReaderChromeAction.SetDialog -> dialog = action.dialog
                        is ReaderChromeAction.JumpToPage -> {
                            jump = ComicViewerJump(++requestId, session.contentSeq, action.pageIndex)
                        }
                        is ReaderChromeAction.ChangeGlobalSettings,
                        is ReaderChromeAction.ChangeWorkOverride,
                        ReaderChromeAction.ResetWorkOverride -> changeSettings(action)
                    }
                })
            }
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (progressFailed || settingsFailed) {
            Snackbar(Modifier.align(Alignment.TopCenter)) {
                val message = if (progressFailed) Res.string.reader_progress_save_failed
                else Res.string.reader_preferences_save_failed
                Text(stringResource(message))
            }
        }
    }
}

private fun ReaderSettings.viewerOptions() = ComicViewerOptions(
    mode = ComicReadingMode.fromId(mode.name),
    pagerLayout = ComicPagerLayout.fromId(layout.name),
    firstPageSingle = firstPageSingle,
    invertDoublePages = dualPageReversed,
    scaleFit = ComicScaleFit.fromId(fit.name),
    cropBorders = cropBorders,
    doubleTapZoom = zoomOnDoubleTap,
)
