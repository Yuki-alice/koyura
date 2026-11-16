package app.koyura.app.reader.chrome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.koyura.data.reader.preferences.ReaderPreferencesStore
import app.koyura.domain.reader.preferences.WorkReaderOverride
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeDialog
import app.koyura.features.reader.comic.chrome.ReaderChromeState
import kotlinx.coroutines.runBlocking

/**
 * Independent chrome host used by behavior and screenshot tests.
 *
 * Applies every [ReaderChromeAction] to [state] the way the production host will: page jumps
 * and chapter switches update the displayed position (progress I/O itself stays outside), settings
 * actions update the effective settings and persist through [store] when one is attached.
 */
class ChromeTestHost(
    initial: ReaderChromeState,
    private val store: ReaderPreferencesStore? = null,
    private val sourceKey: String = "example_source",
    private val workId: String = "demo",
) {
    var state by mutableStateOf(initial)
    val actions = mutableListOf<ReaderChromeAction>()

    fun onAction(action: ReaderChromeAction) {
        actions += action
        state = reduce(state, action)
        persist(action)
    }

    private fun reduce(previous: ReaderChromeState, action: ReaderChromeAction): ReaderChromeState =
        when (action) {
            ReaderChromeAction.Back -> previous
            is ReaderChromeAction.SetMenuVisible -> previous.copy(menuVisible = action.visible)
            is ReaderChromeAction.JumpToPage ->
                previous.copy(pageIndex = action.pageIndex.coerceIn(0, (previous.pageCount - 1).coerceAtLeast(0)))
            is ReaderChromeAction.OpenChapter ->
                previous.copy(
                    chapterId = action.chapterId,
                    pageIndex = 0,
                    dialog = ReaderChromeDialog.None,
                )
            is ReaderChromeAction.ChangeGlobalSettings ->
                previous.copy(global = action.settings)
            is ReaderChromeAction.ChangeWorkOverride -> {
                val base = previous.override ?: WorkReaderOverride()
                previous.copy(override = base.patchedWith(action.patch))
            }
            ReaderChromeAction.ResetWorkOverride -> previous.copy(override = null)
            is ReaderChromeAction.SetDialog -> previous.copy(dialog = action.dialog)
        }

    private fun persist(action: ReaderChromeAction) {
        val target = store ?: return
        runBlocking {
            when (action) {
                is ReaderChromeAction.ChangeGlobalSettings -> target.writeGlobal(action.settings)
                is ReaderChromeAction.ChangeWorkOverride ->
                    target.writeOverride(sourceKey, workId, action.patch)
                ReaderChromeAction.ResetWorkOverride -> target.clearOverride(sourceKey, workId)
                else -> Unit
            }
        }
    }
}
