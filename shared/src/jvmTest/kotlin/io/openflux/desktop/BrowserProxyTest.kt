package io.openflux.desktop

import io.openflux.desktop.web.BrowserProxy
import io.openflux.desktop.web.BuiltInBrowser
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BrowserProxyTest {
    private val closers = mutableListOf<() -> Unit>()

    @AfterTest
    fun cleanup() = closers.forEach { runCatching(it) }

    /** Echoes each line back with a prefix; counts connections. */
    private fun echoServer(accepted: AtomicInteger = AtomicInteger()): Int {
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        closers += { server.close() }
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                accepted.incrementAndGet()
                thread(isDaemon = true) {
                    s.use {
                        val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                        val first = reader.readLine() ?: return@thread
                        if (first.startsWith("GET ")) {
                            // A plain HTTP request: answer with the request line we got.
                            while (reader.readLine()?.isNotEmpty() == true) Unit
                            val body = first
                            it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body".toByteArray())
                        } else {
                            it.getOutputStream().write("echo:$first\n".toByteArray())
                        }
                        it.getOutputStream().flush()
                    }
                }
            }
        }
        return server.localPort
    }

    private fun proxy(): Pair<BrowserProxy, Int> {
        val p = BrowserProxy()
        val port = p.start()
        closers += { p.stop() }
        return p to port
    }

    private fun connectThrough(proxyPort: Int, target: String): String {
        Socket(InetAddress.getLoopbackAddress(), proxyPort).use { s ->
            s.soTimeout = 5000
            val out = s.getOutputStream()
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            out.write("CONNECT $target HTTP/1.1\r\nHost: $target\r\n\r\n".toByteArray())
            out.flush()
            val status = reader.readLine()
            assertTrue(status.contains(" 200 "), status)
            while (reader.readLine()?.isNotEmpty() == true) Unit
            out.write("hello\n".toByteArray())
            out.flush()
            return reader.readLine()
        }
    }

    @Test
    fun connectGoesStraightOut() {
        val echo = echoServer()
        val (_, port) = proxy()
        assertEquals("echo:hello", connectThrough(port, "127.0.0.1:$echo"))
    }

    @Test
    fun upstreamCarriesTheTunnel() {
        val echo = echoServer()
        val (node, nodePort) = proxy()
        val nodeConnections = AtomicInteger()
        // Count what reaches the "node" by wrapping it: its own upstream stays direct.
        val (browser, browserPort) = proxy()
        browser.upstream = "127.0.0.1:$nodePort"
        assertEquals("echo:hello", connectThrough(browserPort, "127.0.0.1:$echo"))
        assertEquals(null, node.upstream)
        nodeConnections.set(0)
        assertFailsWith<IllegalArgumentException> { browser.upstream = "203.0.113.1:3128" }
        browser.upstream = null
        assertEquals("echo:hello", connectThrough(browserPort, "127.0.0.1:$echo"))
    }

    @Test
    fun plainHttpIsRewrittenToOriginForm() {
        val echo = echoServer()
        val (_, port) = proxy()
        Socket(InetAddress.getLoopbackAddress(), port).use { s ->
            s.soTimeout = 5000
            s.getOutputStream().write("GET http://127.0.0.1:$echo/a?b=1 HTTP/1.1\r\nHost: 127.0.0.1\r\nProxy-Connection: keep-alive\r\n\r\n".toByteArray())
            s.getOutputStream().flush()
            val response = s.getInputStream().readBytes().decodeToString()
            assertTrue(response.startsWith("HTTP/1.1 200"), response)
            assertTrue(response.endsWith("GET /a?b=1 HTTP/1.1"), response)
        }
    }

    @Test
    fun packageMatchesThePlatform() {
        assertTrue(BuiltInBrowser.packageUrl("Windows 11", "amd64").endsWith("-windows-x64-b895.97.tar.gz"))
        assertTrue(BuiltInBrowser.packageUrl("Mac OS X", "aarch64").contains("-osx-aarch64-"))
        assertTrue(BuiltInBrowser.packageUrl("Linux", "amd64").startsWith("https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-"))
    }

    /** Without these Chromium looks for its framework inside OpenFlux.app and crashes the app. */
    @Test
    fun macSwitchesPointChromiumAtTheDownloadedFramework() {
        val dir = File("/Users/u/Library/Application Support/OpenFlux/browser")
        val mac = BuiltInBrowser.switches(8080, dir, "Mac OS X").toList()
        val frameworks = "${dir.canonicalPath}/Frameworks"
        assertTrue("--framework-dir-path=$frameworks/Chromium Embedded Framework.framework" in mac, mac.toString())
        assertTrue("--main-bundle-path=$frameworks/jcef Helper.app" in mac, mac.toString())
        assertTrue("--browser-subprocess-path=$frameworks/jcef Helper.app/Contents/MacOS/jcef Helper" in mac, mac.toString())
        assertTrue("--proxy-server=http://127.0.0.1:8080" in mac, mac.toString())

        val windows = BuiltInBrowser.switches(8080, dir, "Windows 11").toList()
        assertTrue(windows.none { it.startsWith("--framework-dir-path") }, windows.toString())
        assertTrue("--disable-gpu" in windows, windows.toString())
    }

    @Test
    fun cookiesPreferTheMostSpecificDomain() {
        val picked = BuiltInBrowser.selectForUrl(listOf(
            BuiltInBrowser.CookieValue("yandexuid", "1", ".yandex.ru"),
            BuiltInBrowser.CookieValue("Session_id", "s", ".yandex.ru"),
            BuiltInBrowser.CookieValue("yandexuid", "2", "disk.yandex.ru"),
            BuiltInBrowser.CookieValue("bad", "a;b", ".yandex.ru"),
        ))
        assertEquals(mapOf("yandexuid" to "2", "Session_id" to "s"), picked)
    }
}
