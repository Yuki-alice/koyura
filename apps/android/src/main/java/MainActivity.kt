package app.koyura.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.koyura.app.App
import app.koyura.app.registerAndroidWebLoginBrowser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerAndroidWebLoginBrowser(::AndroidWebLoginBrowser)
        setContent { App() }
    }
}
