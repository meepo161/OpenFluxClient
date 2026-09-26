package io.openflux.desktop

import io.openflux.desktop.web.BuiltInBrowser
import kotlinx.coroutines.runBlocking
import org.cef.CefApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuiltInBrowserTest {
    /**
     * The wizard and the captcha wipe cookies before they open a page. Before
     * the browser runs that must not touch CEF: CefCookieManager would start
     * CEF with default settings, and KCEF then fails with "Must be called
     * before CefApp is initialized" and hangs on the next try.
     */
    @Test
    fun cookiesBeforeStartDoNotStartCef() {
        BuiltInBrowser.clearCookies()
        assertEquals(emptyList(), runBlocking { BuiltInBrowser.cookies("https://disk.yandex.ru/") })
        assertNull(CefApp.getInstanceIfAny())
    }
}
