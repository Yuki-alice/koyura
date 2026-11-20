package app.koyura.app

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun ReaderSystemBars(hidden: Boolean) {
    if (!hidden) return
    val view = LocalView.current
    DisposableEffect(view) {
        val restore = hideSystemBars(view)
        onDispose { restore() }
    }
}

@Suppress("DEPRECATION")
private fun hideSystemBars(view: View): () -> Unit {
    if (Build.VERSION.SDK_INT >= 30) {
        val controller = view.windowInsetsController ?: return {}
        val status = WindowInsets.Type.statusBars()
        val navigation = WindowInsets.Type.navigationBars()
        val insets = view.rootWindowInsets
        val previousBehavior = controller.systemBarsBehavior
        val visible = (if (insets?.isVisible(status) != false) status else 0) or
            (if (insets?.isVisible(navigation) != false) navigation else 0)
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(status or navigation)
        return {
            controller.systemBarsBehavior = previousBehavior
            controller.show(visible)
        }
    }

    val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    val previousFlags = view.systemUiVisibility and flags
    view.systemUiVisibility = view.systemUiVisibility or flags
    return { view.systemUiVisibility = (view.systemUiVisibility and flags.inv()) or previousFlags }
}
