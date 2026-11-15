package dev.reader.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.reader.app.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Reader") {
        App()
    }
}
