package io.openflux.desktop.core

import io.openflux.desktop.ui.BrowserPage
import io.openflux.desktop.web.BrowserProxy
import io.openflux.desktop.web.BuiltInBrowser
import io.openflux.desktop.web.KcefPage
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

    /** The cookies for [url] the check left, by name (the most specific domain wins). */
    suspend fun collect(url: String): Map<String, String> {
        check(_page.value != null) { "Сначала откройте страницу проверки" }
        val now = Date()
        val cookies = BuiltInBrowser.cookies(url).filter { !it.hasExpires || it.expires == null || it.expires.after(now) }
        return BuiltInBrowser.selectForUrl(cookies.map { BuiltInBrowser.CookieValue(it.name, it.value, it.domain.orEmpty()) })
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
