package io.openflux.android.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import io.openflux.desktop.ui.BrowserPage
import io.openflux.desktop.ui.BrowserViews
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * One page of the built-in browser: a WebView the UI shows with
 * [WebBrowserViews]. It is created the first time it is shown and kept until
 * [close], so hiding the dialog does not lose the page. [proxy] ("host:port")
 * sends it through the tunnel, for a check the exit node must pass from its
 * own address.
 */
class WebPage(
    private val startUrl: String,
    private val proxy: String = "",
    private val scripts: Boolean = false,
) : BrowserPage {
    @Volatile var url: String = startUrl
        private set
    @Volatile var loading = true
        private set
    @Volatile var closed = false
        private set

    private var view: WebView? = null
    private var proxyOverridden = false
    private val results = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val ids = AtomicLong()

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    internal fun view(context: Context): WebView {
        view?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val web = WebView(context)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // The UA the core fetches the document with: a check's pass may be bound to it.
            userAgentString = USER_AGENT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        if (scripts) web.addJavascriptInterface(Results(), BRIDGE)
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                this@WebPage.url = url
                loading = true
            }

            override fun onPageFinished(view: WebView, url: String) {
                this@WebPage.url = url
                loading = false
            }
        }
        view = web
        if (proxy.isEmpty()) {
            web.loadUrl(startUrl)
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            proxyOverridden = true
            val config = ProxyConfig.Builder().addProxyRule(proxy).build()
            ProxyController.getInstance().setProxyOverride(config, Runnable::run) { if (!closed) web.loadUrl(startUrl) }
        } else {
            web.loadData(
                "<p style='font:16px sans-serif;padding:16px'>WebView не умеет работать через прокси: обновите «Android System WebView» в Google Play.</p>",
                "text/html; charset=utf-8", "utf-8",
            )
        }
        return web
    }

    /** Runs [expression] (it may return a Promise) on the page; its value as a string. */
    suspend fun evaluate(expression: String, timeoutMs: Long = 90_000): String {
        check(scripts) { "This page does not run scripts" }
        val id = ids.incrementAndGet().toString()
        val result = CompletableDeferred<String>().also { results[id] = it }
        try {
            withContext(Dispatchers.Main) {
                val web = view ?: throw IllegalStateException("Страница ещё не открыта")
                web.evaluateJavascript(
                    """
                    (async () => {
                      let v;
                      try { v = await ($expression); } catch (e) { v = JSON.stringify({state: 'fail', error: String((e && e.message) || e)}); }
                      window.$BRIDGE.result('$id', typeof v === 'string' ? v : JSON.stringify(v));
                    })();
                    """.trimIndent(),
                    null,
                )
            }
            return withTimeout(timeoutMs) { result.await() }
        } finally {
            results.remove(id)
        }
    }

    /** The Cookie header the page's cookie jar sends to [url]. */
    fun cookieHeader(url: String): String = CookieManager.getInstance().getCookie(url).orEmpty()

    fun close() {
        if (closed) return
        closed = true
        results.values.forEach { it.cancel() }
        val web = view
        view = null
        val overridden = proxyOverridden
        web?.post {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
            // The override applies to every WebView in the process.
            if (overridden) ProxyController.getInstance().clearProxyOverride(Runnable::run) {}
        }
    }

    private inner class Results {
        @JavascriptInterface
        fun result(id: String, value: String) {
            results[id]?.complete(value)
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0"
        private const val BRIDGE = "OpenFluxBridge"

        /** Every cookie of the built-in browser, the Yandex sign-in among them. */
        fun clearCookies() {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }
    }
}

/** Shows [WebPage]s inside the Compose UI. */
object WebBrowserViews : BrowserViews {
    @Composable
    override fun Page(page: BrowserPage, modifier: Modifier) {
        val web = page as? WebPage ?: return
        key(web) {
            AndroidView(factory = { context -> web.view(context) }, modifier = modifier)
        }
    }
}
