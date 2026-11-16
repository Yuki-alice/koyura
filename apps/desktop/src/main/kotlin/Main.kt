package app.koyura.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.koyura.app.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Koyura") {
        App()
    }
}
