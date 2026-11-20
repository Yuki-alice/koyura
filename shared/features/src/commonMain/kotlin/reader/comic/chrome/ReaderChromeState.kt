package app.koyura.features.reader.comic.chrome

import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.domain.reader.preferences.WorkReaderOverridePatch
import app.koyura.domain.reader.preferences.withOverride
import app.koyura.features.reader.comic.viewer.ComicTapZoneMode

/** One chapter in the chapter list. */
data class ChapterEntry(
    val id: String?,
    val title: String,
)

/**
 * Everything [ReaderChrome] renders, owned by the host.
 *
 * Page indices are 0-based; the chrome displays them 1-based. Menu visibility, dialog selection
 * and navigation availability are all driven from here so tests and hosts stay deterministic.
 * Reading or writing progress, actually jumping pages and opening chapters are the host's job:
 * see [ReaderChromeAction].
 */
data class ReaderChromeState(
    /** Work title shown in the top bar. */
    val workTitle: String,
    /** Currently opened chapter ID (`null` for chapterless works). */
    val chapterId: String?,
    /** Chapters in display order. */
    val chapters: List<ChapterEntry>,
    /** Currently displayed 0-based page index. */
    val pageIndex: Int,
    /** Total page count of the current chapter. */
    val pageCount: Int,
    /** Whether the top/bottom bars are visible. */
    val menuVisible: Boolean,
    /** Previous-chapter navigation availability (false at the first chapter). */
    val hasPrevChapter: Boolean,
    /** Next-chapter navigation availability (false at the last chapter). */
    val hasNextChapter: Boolean,
    /** Global reader defaults. */
    val global: ReaderSettings,
    /** Per-work override for `(sourceKey, workId)`, or null when it follows the global. */
    val override: WorkReaderOverride?,
    /** Which dialog is open, if any. */
    val dialog: ReaderChromeDialog = ReaderChromeDialog.None,
    /**
     * 点击区布局（Komikku navigation mode 选择）。持久化契约已就位
     * （`ReaderSettings.tapZone/invertTapZone`，含旧配置兼容），由宿主持有、
     * 阅读器轮次把此会话字段切到偏好读写链；默认 DEFAULT 即既有三分区行为。
     */
    val tapZone: ComicTapZoneMode = ComicTapZoneMode.DEFAULT,
    /** 点击区反转：交换上一页/下一页区域（菜单区不受影响）。 */
    val invertTapZone: Boolean = false,
) {
    /** Effective settings: the override merged over the global defaults. */
    val effective: ReaderSettings get() = global.withOverride(override)

    /** Index of the current chapter in [chapters], or -1 when absent. */
    val currentChapterIndex: Int get() = chapters.indexOfFirst { it.id == chapterId }
}

/** Dialogs the chrome can show. Only one is open at a time. */
enum class ReaderChromeDialog {
    None,
    ChapterList,
    ReadingMode,
    Settings,
    PageJump,
}

/**
 * UI intents emitted by [ReaderChrome]. The host applies them: it owns progress I/O, the actual
 * page jump and chapter switches. Slider drags only preview; [JumpToPage] fires once on release.
 * Previous/next chapter reuse [OpenChapter].
 */
sealed interface ReaderChromeAction {
    /** Back control: the host saves progress first, then pops. */
    data object Back : ReaderChromeAction

    /** Show or hide the top/bottom bars. */
    data class SetMenuVisible(val visible: Boolean) : ReaderChromeAction

    /** Jump to a 0-based page; emitted once per confirmed gesture. */
    data class JumpToPage(val pageIndex: Int) : ReaderChromeAction

    /** Open a chapter by ID, for list picks and prev/next navigation alike. */
    data class OpenChapter(val chapterId: String?) : ReaderChromeAction

    /** Replace the global defaults (persisted by the host). */
    data class ChangeGlobalSettings(val settings: ReaderSettings) : ReaderChromeAction

    /** Merge [patch] into the work override field-by-field (persisted by the host). */
    data class ChangeWorkOverride(val patch: WorkReaderOverridePatch) : ReaderChromeAction

    /** Drop the work override so the work inherits the global defaults again. */
    data object ResetWorkOverride : ReaderChromeAction

    /** Open or close a dialog. */
    data class SetDialog(val dialog: ReaderChromeDialog) : ReaderChromeAction

    /** 切换点击区布局（点击区契约未进 ReaderSettings，由宿主持有，见交付报告）。 */
    data class SetTapZone(val zone: ComicTapZoneMode) : ReaderChromeAction

    /** 切换点击区反转。 */
    data class SetInvertTapZone(val invert: Boolean) : ReaderChromeAction
}
