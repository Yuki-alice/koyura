package app.koyura.app.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import app.koyura.app.browse.AppCoverDecoder
import app.koyura.app.browse.BrowseSession
import app.koyura.app.navigation.AppNavigationState
import app.koyura.app.navigation.ComicReaderRoute
import app.koyura.app.navigation.SourceDetailRoute
import app.koyura.designsystem.navigation.NavTab
import app.koyura.features.library.LibraryScreen
import app.koyura.features.library.LibrarySession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 书架页签内容：真实书架屏 + 回详情/进章节的装配接线。
 *
 * - 会话由 [AppScaffold] 持有（与底栏隐藏、返回消费共享同一实例），本页只消费。
 * - 卡片点开推详情路由；继续阅读先经浏览服务解析最新目录，再推阅读器路由，
 *   章节解析失败只记页内错误、不导航（已提交快照原样保留）。
 * - 从阅读器返回（本 tab 栈收缩）后重读快照，书架即显示最新进度。
 */
@Composable
fun LibraryTabContent(
    navigation: AppNavigationState,
    session: LibrarySession,
    browseSession: BrowseSession,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(navigation.currentStack.size) {
        if (navigation.selectedTab == NavTab.Library) {
            session.refresh()
        }
    }
    LibraryScreen(
        session = session,
        decodeCover = { bytes -> AppCoverDecoder.decode(bytes) },
        onOpenWork = { sourceKey, workId, title ->
            navigation.push(SourceDetailRoute(sourceKey, workId, title))
        },
        onContinue = { entry ->
            scope.launch {
                try {
                    val detail = browseSession.loadWorkDetail(entry.sourceKey, entry.workId)
                    val chapters = detail.chapters
                    if (chapters.isEmpty()) {
                        session.noteError("No chapters listed for ${entry.displayTitle}")
                        return@launch
                    }
                    val ids = chapters.map { it.id }
                    val titles = chapters.map { it.title }
                    val target = entry.continueChapterId?.takeIf { it in ids } ?: ids.first()
                    navigation.push(
                        ComicReaderRoute(
                            sourceKey = entry.sourceKey,
                            workId = entry.workId,
                            chapterId = target,
                            title = entry.displayTitle,
                            chapterIds = ids,
                            chapterTitles = titles,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    session.noteError(failure.message?.takeIf { it.isNotBlank() } ?: "Cannot continue reading")
                }
            }
        },
    )
}
