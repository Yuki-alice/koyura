package app.koyura.android

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.view.WindowManager
import androidx.webkit.CookieManagerCompat
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.koyura.data.extension.WebLoginBrowser
import app.koyura.data.extension.WebLoginCapture
import app.koyura.data.extension.WebLoginMonitor
import app.koyura.data.extension.WebLoginPage
import app.koyura.source.runtime.SourceCookie
import io.ktor.http.parseServerSetCookieHeader
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

internal class AndroidWebLoginBrowser(private val activity: Activity) : WebLoginBrowser {
    override suspend fun open(url: String, checkStatus: suspend (WebLoginPage) -> Boolean): WebLoginCapture? =
        withContext(Dispatchers.Main.immediate) {
            loginMutex.withLock { openDialog(url, checkStatus) }
        }

    private suspend fun openDialog(url: String, checkStatus: suspend (WebLoginPage) -> Boolean): WebLoginCapture? {
        require(WebLoginMonitor.allowedUrl(url))
        check(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            "Update Android System WebView to sign in"
        }
        if (activity.isFinishing || activity.isDestroyed) return null
        val store = ProfileStore.getInstance()
        store.allProfileNames.filter { it.startsWith(PROFILE_PREFIX) }.forEach {
            runCatching { store.deleteProfile(it) }
        }
        val name = PROFILE_PREFIX + UUID.randomUUID()
        val view = WebView(activity)
        val dismissed = CompletableDeferred<Unit>()
        var revision = 0L
        var loading = true
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        val cookies = try {
            WebViewCompat.setProfile(view, name)
            WebViewCompat.getProfile(view).cookieManager
        } catch (failure: Throwable) {
            view.destroy()
            runCatching { store.deleteProfile(name) }
            throw failure
        }
        try {
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportMultipleWindows(false)
            }
            cookies.setAcceptThirdPartyCookies(view, false)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !WebLoginMonitor.allowedUrl(request.url.toString())

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    revision++
                    loading = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    dismissed.complete(Unit)
                    return true
                }
            }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {}
                override fun onViewDetachedFromWindow(view: View) { dismissed.complete(Unit) }
            })
            dialog.setOnDismissListener { dismissed.complete(Unit) }
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.show()
            view.loadUrl(url)
            fun page(): WebLoginPage? =
                view.url?.takeUnless { loading || dismissed.isCompleted }?.let { WebLoginPage(it, view.title.orEmpty()) }
            return WebLoginMonitor.awaitCapture(dismissed, ::page, checkStatus) { verified ->
                val snapshot = revision
                val encoded = suspendCancellableCoroutine { continuation ->
                    view.evaluateJavascript(WebLoginMonitor.STORAGE_SCRIPT) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                }
                val result = runCatching { Json.parseToJsonElement(encoded).jsonPrimitive.content }.getOrNull()
                val storage = WebLoginMonitor.storage(verified, result) ?: return@awaitCapture null
                if (revision != snapshot || page() != verified) return@awaitCapture null
                val captured = CookieManagerCompat.getCookieInfo(cookies, verified.url).mapNotNull { header ->
                    runCatching { parseServerSetCookieHeader(header) }.getOrNull()?.let {
                        SourceCookie(it.name, it.value, it.domain, it.path, it.secure, it.httpOnly, it.expires?.timestamp, it.maxAge)
                    }
                }
                WebLoginCapture(verified, captured, storage)
            }
        } finally {
            dialog.dismiss()
            view.stopLoading()
            view.destroy()
            withContext(NonCancellable) {
                withTimeoutOrNull(2_000) {
                    suspendCancellableCoroutine { continuation ->
                        cookies.removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
                    }
                }
                runCatching { store.deleteProfile(name) }
            }
        }
    }

    private companion object {
        val loginMutex = Mutex()
        const val PROFILE_PREFIX = "source-web-login-"
    }
}
