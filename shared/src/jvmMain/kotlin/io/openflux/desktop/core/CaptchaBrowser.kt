package io.openflux.desktop.core

import io.openflux.desktop.ui.BrowserPage
import io.openflux.desktop.web.BrowserProxy
import io.openflux.desktop.web.BuiltInBrowser
import io.openflux.desktop.web.KcefPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.util.Date

/**
 * A Yandex check in the built-in browser, through the node's proxy when the
 * node asked for it. Cookies are read only when the user submits the check,
 * then wiped from the browser.
 */
class CaptchaBrowser : AutoCloseable {
    private val _page = MutableStateFlow<BrowserPage?>(null)
    val page: StateFlow<BrowserPage?> = _page.asStateFlow()

    suspend fun open(request: IpcCookiesRequest, onStep: (String) -> Unit) {
        require(URI(request.url).scheme == "https") { "The check URL must use HTTPS" }
        close()
        val upstream = if (request.remote) {
            request.proxy.also { require(BrowserProxy.isLoopback(it)) { "Проверке ноды нужен локальный прокси" } }
        } else null
        BuiltInBrowser.clearCookies()
        _page.value = BuiltInBrowser.open(request.url, upstream, onStep)
    }

    /**
     * The cookies the check left for [url] and for the page it ended on, by
     * name (the most specific domain wins), like the Android CaptchaActivity.
     */
    suspend fun collect(url: String): Map<String, String> {
        val page = _page.value as? KcefPage ?: throw IllegalStateException("Сначала откройте страницу проверки")
        val now = Date()
        val urls = listOf(url, page.url).filter { it.startsWith("https://") }.distinct()
        val cookies = urls.flatMap { BuiltInBrowser.cookies(it) }
            .filter { !it.hasExpires || it.expires == null || it.expires.after(now) }
        return BuiltInBrowser.selectForUrl(cookies.map { BuiltInBrowser.CookieValue(it.name, it.value, it.domain.orEmpty()) })
    }

    /**
     * Returns once the page has settled on a regular page, not a check: a
     * real browser is often let through without any (the check targets the
     * core's bot-like client), and the Android app then submits by itself.
     * Returns false if the page is closed first.
     */
    suspend fun awaitPassed(): Boolean {
        var settledSince = 0L
        while (true) {
            val page = _page.value as? KcefPage ?: return false
            if (page.closed) return false
            val url = page.url
            val settled = !page.loading && url.startsWith("https://") && !isCheckpoint(url)
            val now = System.currentTimeMillis()
            if (!settled) settledSince = 0L
            else if (settledSince == 0L) settledSince = now
            else if (now - settledSince >= SETTLE_MS) return true
            delay(300)
        }
    }

    companion object {
        private const val SETTLE_MS = 1500L

        fun isCheckpoint(url: String) = "showcaptcha" in url || "passport.yandex" in url
    }

    override fun close() {
        val current = _page.value
        _page.value = null
        (current as? KcefPage)?.close()
        if (current != null) {
            BuiltInBrowser.release()
            BuiltInBrowser.clearCookies()
        }
    }
}
