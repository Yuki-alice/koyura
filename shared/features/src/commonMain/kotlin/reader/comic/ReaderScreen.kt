package app.koyura.features.reader.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppSnackbar
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme
import app.koyura.domain.reader.preferences.ReaderBackground
import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.domain.reader.preferences.ReaderPreferences
import app.koyura.domain.reader.preferences.ReaderPreferencesSnapshot
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.domain.reader.preferences.patchedWith
import app.koyura.domain.reader.preferences.withOverride
import app.koyura.features.reader.comic.chrome.ChapterEntry
import app.koyura.features.reader.comic.chrome.ReaderChrome
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeDialog
import app.koyura.features.reader.comic.chrome.ReaderChromeState
import app.koyura.features.reader.comic.chrome.toViewerZone
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
    fallbackBackground: ReaderBackground = ReaderBackground.AUTOMATIC,
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
    // 点击区走偏好链（全局 + 覆盖合并），默认 DEFAULT 即既有三分区行为。
    val settingWrites = remember(preferences, sourceKey, workId) { Mutex() }

    LaunchedEffect(preferences, sourceKey, workId, attempt) {
        if (settings == null) {
            try {
                settings = preferences?.load(sourceKey, workId)
                    ?: ReaderPreferencesSnapshot(
                        global = ReaderSettings(layout = ReaderLayout.SINGLE_PAGE, background = fallbackBackground),
                    )
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

    // 已加载的作品和全局选择优先；读取期间使用装配层背景。
    val resolvedBackground = settings
        ?.let { it.global.withOverride(it.override).background }
        ?: fallbackBackground
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerBackgroundColor(resolvedBackground, isSystemInDarkTheme()))
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
                AppText(
                    text = stringResource(Res.string.reader_load_failed, loadError),
                    color = UiTheme.colorScheme.error,
                )
                AppButton(onClick = { attempt++ }) { AppText(stringResource(Res.string.reader_retry)) }
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
                    AppText(stringResource(Res.string.reader_empty_pages), Modifier.align(Alignment.Center))
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
                val chromeContent: @Composable () -> Unit = {
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
                // Black/Gray 两档强制暗色 chrome（原版 ContextExtensions.kt:111-118 的 night 上下文语义）。
                if (resolvedBackground == ReaderBackground.BLACK || resolvedBackground == ReaderBackground.GRAY) {
                    ReaderTheme(darkTheme = true) { chromeContent() }
                } else {
                    chromeContent()
                }
            }
            else -> AppCircularProgress(Modifier.align(Alignment.Center))
        }
        if (progressFailed || settingsFailed) {
            AppSnackbar(Modifier.align(Alignment.TopCenter)) {
                val message = if (progressFailed) Res.string.reader_progress_save_failed
                else Res.string.reader_preferences_save_failed
                AppText(stringResource(message))
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
    tapZone = tapZone.toViewerZone(),
    invertTapZone = invertTapZone,
)

/**
 * Automatic 背景映射（默认阅读背景，白皮书 07 §7.5）：暗 #FF202125 / 亮 #FFFFFFFF。
 *
 * Code: ReaderActivity.kt:1432 `automaticBackgroundColor()`（暗走 `grayBackgroundColor` =
 * `Color.rgb(0x20, 0x21, 0x25)`，亮走 `Color.WHITE`；偏好索引 3 即 Automatic）。
 * 不用主题 token：Automatic 下根背景固定映射、chrome 跟随主题。
 *
 * 四档（Black/Gray/Automatic/White）见 [readerBackgroundColor]；Black/Gray 档由根组件用
 * `ReaderTheme(darkTheme = true)` 包 chrome——原版 `ContextExtensions.kt:111-118`
 * 对 Black/Gray 强制 night 上下文的耦合语义。
 */
@Composable
fun readerAutomaticBackground(dark: Boolean = isSystemInDarkTheme()): Color =
    readerBackgroundColor(ReaderBackground.AUTOMATIC, dark)

/**
 * 四档阅读背景色（白皮书 07 §7.5 / 原版 `ReaderActivity.automaticBackgroundColor()`）：
 * - AUTOMATIC：暗 `#FF202125`、亮白；
 * - BLACK：纯黑；
 * - GRAY：`#FF202125`（两档 chrome 均走暗色主题，见调用点）；
 * - WHITE：纯白（chrome 跟随主题）。
 *
 * 纯函数（暗色入参显式），便于直接断言四档色值。
 */
fun readerBackgroundColor(
    background: ReaderBackground,
    dark: Boolean,
): Color = when (background) {
    ReaderBackground.AUTOMATIC -> if (dark) Color(0xFF202125) else Color.White
    ReaderBackground.BLACK -> Color.Black
    ReaderBackground.GRAY -> Color(0xFF202125)
    ReaderBackground.WHITE -> Color.White
}
