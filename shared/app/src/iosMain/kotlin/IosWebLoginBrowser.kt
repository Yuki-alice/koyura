@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.koyura.app

import app.koyura.data.extension.WebLoginBrowser
import app.koyura.data.extension.WebLoginCapture
import app.koyura.data.extension.WebLoginMonitor
import app.koyura.data.extension.WebLoginPage
import app.koyura.source.runtime.SourceCookie
import io.ktor.http.Url
import kotlin.coroutines.resume
import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSSelectorFromString
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem.UIBarButtonSystemItemCancel
import platform.UIKit.UINavigationController
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.navigationItem
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationActionPolicy.WKNavigationActionPolicyAllow
import platform.WebKit.WKNavigationActionPolicy.WKNavigationActionPolicyCancel
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

internal class IosWebLoginBrowser : WebLoginBrowser {
    override suspend fun open(url: String, checkStatus: suspend (WebLoginPage) -> Boolean): WebLoginCapture? =
        withContext(Dispatchers.Main.immediate) {
            loginMutex.withLock { present(url, checkStatus) }
        }

    private suspend fun present(url: String, checkStatus: suspend (WebLoginPage) -> Boolean): WebLoginCapture? {
        require(WebLoginMonitor.allowedUrl(url))
        val presenter = activePresenter() ?: return null
        val store = WKWebsiteDataStore.nonPersistentDataStore()
        val configuration = WKWebViewConfiguration().apply { websiteDataStore = store }
        val view = WKWebView(frame = presenter.view.bounds, configuration = configuration)
        val controller = LoginController(view)
        val navigation = UINavigationController(rootViewController = controller)
        navigation.modalPresentationStyle = UIModalPresentationFullScreen
        val delegate = LoginNavigation(controller.dismissed)
        view.navigationDelegate = delegate
        presenter.presentViewController(navigation, animated = true, completion = null)
        try {
            view.loadRequest(NSURLRequest(checkNotNull(NSURL.URLWithString(url))))
            fun page(): WebLoginPage? =
                view.URL?.absoluteString?.takeUnless { view.loading || controller.dismissed.isCompleted }
                    ?.let { WebLoginPage(it, view.title.orEmpty()) }
            return WebLoginMonitor.awaitCapture(controller.dismissed, ::page, checkStatus) { verified ->
                val snapshot = delegate.revision
                val result = suspendCancellableCoroutine { continuation ->
                    view.evaluateJavaScript(WebLoginMonitor.STORAGE_SCRIPT) { value, _ ->
                        if (continuation.isActive) continuation.resume(value as? String)
                    }
                }
                val storage = WebLoginMonitor.storage(verified, result) ?: return@awaitCapture null
                val cookies = suspendCancellableCoroutine { continuation ->
                    store.httpCookieStore.getAllCookies { values ->
                        if (continuation.isActive) continuation.resume(values.orEmpty().filterIsInstance<NSHTTPCookie>())
                    }
                }
                if (snapshot != delegate.revision || page() != verified) return@awaitCapture null
                val captured = cookies.mapNotNull { cookie ->
                    val host = Url(verified.url).host.lowercase()
                    val domain = cookie.domain.lowercase().trimStart('.')
                    val domainCookie = cookie.domain.startsWith('.')
                    if (host != domain && (!domainCookie || !host.endsWith(".$domain"))) return@mapNotNull null
                    SourceCookie(
                        cookie.name, cookie.value, domain.takeIf { domainCookie },
                        cookie.path, cookie.secure, cookie.HTTPOnly,
                        cookie.expiresDate?.timeIntervalSince1970?.times(1000)?.toLong(),
                    )
                }
                WebLoginCapture(verified, captured, storage)
            }
        } finally {
            view.stopLoading()
            view.navigationDelegate = null
            navigation.dismissViewControllerAnimated(true, completion = null)
        }
    }

    private fun activePresenter(): UIViewController? {
        val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        val scene = scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: return null
        var presenter = scene.windows.filterIsInstance<UIWindow>().firstOrNull { it.keyWindow }?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        return presenter
    }

    private class LoginController(private val browser: WKWebView) : UIViewController(nibName = null, bundle = null) {
        val dismissed = CompletableDeferred<Unit>()

        override fun loadView() {
            view = browser
            navigationItem.leftBarButtonItem = UIBarButtonItem(
                barButtonSystemItem = UIBarButtonSystemItemCancel, target = this, action = NSSelectorFromString("cancelLogin"),
            )
        }

        @ObjCAction
        fun cancelLogin() {
            dismissed.complete(Unit)
        }
    }

    private class LoginNavigation(private val dismissed: CompletableDeferred<Unit>) : NSObject(), WKNavigationDelegateProtocol {
        var revision = 0L
            private set

        override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
            revision++
        }

        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit,
        ) {
            val url = decidePolicyForNavigationAction.request.URL?.absoluteString
            decisionHandler(if (url != null && WebLoginMonitor.allowedUrl(url)) {
                WKNavigationActionPolicyAllow
            } else WKNavigationActionPolicyCancel)
        }

        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
            dismissed.complete(Unit)
        }
    }

    private companion object {
        val loginMutex = Mutex()
    }
}
