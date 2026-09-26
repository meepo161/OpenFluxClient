package io.openflux.desktop.web

import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import dev.datlag.kcef.KCEFClient
import io.openflux.desktop.data.AppDirs
import io.openflux.desktop.ui.BrowserPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import java.awt.Component
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** A page open in the built-in browser. Close it when done. */
class KcefPage internal constructor(private val browser: KCEFBrowser) : BrowserPage {
    /** The browser's native view, for a SwingPanel. */
    val component: Component get() = browser.uiComponent

    val url: String get() = browser.url.orEmpty()

    @Volatile var closed = false
        private set

    fun load(url: String) = browser.loadURL(url)

    /**
     * Runs [expression] (a JavaScript expression, a Promise is awaited) in
     * the page and returns its value as a string. A thrown error comes back
     * as {"state":"fail","error":...}.
     */
    suspend fun evaluate(expression: String, timeoutMs: Long = 90_000): String {
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<String>()
        BuiltInBrowser.pending[id] = result
        val script = """
            Promise.resolve().then(() => ($expression))
              .then(r => String(r), e => JSON.stringify({state: 'fail', error: String((e && e.message) || e)}))
              .then(r => window.${BuiltInBrowser.QUERY}({request: ${JsonPrimitive(id)} + '|' + r, onSuccess: function () {}, onFailure: function () {}}));
        """.trimIndent()
        try {
            browser.executeJavaScript(script, browser.url, 0)
            return withTimeout(timeoutMs) { result.await() }
        } finally {
            BuiltInBrowser.pending.remove(id)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { browser.dispose() }
    }
}

/**
 * Chromium inside OpenFlux (KCEF, JetBrains' JCEF), for Yandex: signing in
 * to create a channel's document and the checks Yandex shows. It is
 * downloaded on first use to the local app data folder. The cache lives in
 * memory and cookies are wiped after each use, so a login stays only in what
 * the caller took from [cookies]. All traffic goes through [proxy].
 */
object BuiltInBrowser {
    internal const val QUERY = "openfluxQuery"
    internal val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

    val proxy = BrowserProxy()
    private val lock = Mutex()
    private var client: KCEFClient? = null

    private val installDir: File
        get() {
            val local = System.getenv("LOCALAPPDATA")?.let { File(it, "OpenFlux") }
            return File(local ?: AppDirs.config, "browser")
        }

    /**
     * Opens [url] in a new page; with [upstream] ("127.0.0.1:port") the page
     * goes out through the node's proxy. [onStep] reports the first-run
     * download.
     */
    suspend fun open(url: String, upstream: String? = null, onStep: (String) -> Unit = {}): KcefPage {
        val client = client(onStep)
        proxy.upstream = upstream
        return withContext(Dispatchers.Swing) {
            KcefPage(client.createBrowser(url, CefRendering.DEFAULT, false))
        }
    }

    /** Cookies the browser would send to [url], HTTP-only ones included. */
    suspend fun cookies(url: String): List<CefCookie> {
        val found = Collections.synchronizedList(mutableListOf<CefCookie>())
        val done = CompletableDeferred<Unit>()
        val started = CefCookieManager.getGlobalManager().visitUrlCookies(url, true) { cookie, count, total, _ ->
            if (cookie != null) found += cookie
            if (count >= total - 1) done.complete(Unit)
            true
        }
        check(started) { "Встроенный браузер не отдал cookies" }
        // CEF never calls the visitor when there are no cookies.
        withTimeoutOrNull(1500) { done.await() }
        return found.toList()
    }

    /** Forgets every cookie: a sign-in must not outlive what it was made for. */
    fun clearCookies() {
        runCatching { CefCookieManager.getGlobalManager().deleteCookies(null, null) }
    }

    /** Back to direct traffic once a page that used the node's proxy is closed. */
    fun release() {
        proxy.upstream = null
    }

    private suspend fun client(onStep: (String) -> Unit): KCEFClient = lock.withLock {
        client?.let { return it }
        val port = proxy.start()
        val initialized = CompletableDeferred<Unit>()
        var last = ""
        val step = { text: String -> if (text != last) { last = text; onStep(text) } }
        var error: Throwable? = null
        var restart = false
        withContext(Dispatchers.IO) {
            KCEF.init(
                builder = {
                    installDir(installDir)
                    download { custom(packageUrl()) }
                    progress {
                        onLocating { step("Готовлю встроенный браузер…") }
                        onDownloading {
                            // The CDN does not always say the size: then no percent.
                            val percent = it.roundToInt()
                            step("Скачиваю встроенный браузер (около 230 МБ, один раз)…" + if (percent in 1..99) " $percent%" else "")
                        }
                        onExtracting { step("Распаковываю встроенный браузер…") }
                        onInitializing { step("Запускаю встроенный браузер…") }
                        onInitialized { initialized.complete(Unit) }
                    }
                    settings {
                        cachePath = null
                        locale = "ru-RU"
                        // JCEF's defaults point into the running JVM's java.home; empty
                        // paths make KCEF use the downloaded runtime instead.
                        resourcesDirPath = null
                        localesDirPath = null
                        browserSubProcessPath = null
                    }
                    // Our own list, not JCEF's defaults (which pin the scale factor to 1 and
                    // blur HiDPI screens). No GPU: a sign-in page does not need it, and a
                    // bad driver would take the browser down.
                    args(
                        "--disable-features=SpareRendererForSitePerProcess",
                        "--disable-gpu",
                        "--proxy-server=http://127.0.0.1:$port",
                        "--proxy-bypass-list=<-loopback>",
                    )
                },
                onError = { error = it },
                onRestartRequired = { restart = true },
            )
        }
        if (restart) throw IllegalStateException("Встроенный браузер скачан: перезапустите OpenFlux и повторите")
        error?.let { throw IllegalStateException("Встроенный браузер не запустился: ${it.message ?: it::class.simpleName}") }
        withTimeoutOrNull(60_000) { initialized.await() }
            ?: throw IllegalStateException("Встроенный браузер не запустился")
        val created = KCEF.newClient()
        val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig(QUERY, "${QUERY}Cancel"))
        router.addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?,
                frame: CefFrame?,
                queryId: Long,
                request: String?,
                persistent: Boolean,
                callback: CefQueryCallback?,
            ): Boolean {
                val text = request ?: return false
                val deferred = pending.remove(text.substringBefore('|')) ?: return false
                deferred.complete(text.substringAfter('|'))
                callback?.success("")
                return true
            }
        }, true)
        created.addMessageRouter(router)
        client = created
        created
    }

    /**
     * The JetBrains Runtime build with JCEF that matches the JCEF classes
     * KCEF ships, pinned: "latest" from GitHub may not fit them, and the
     * JetBrains CDN needs no GitHub API.
     */
    internal fun packageUrl(
        os: String = System.getProperty("os.name"),
        arch: String = System.getProperty("os.arch"),
    ): String {
        val platform = when {
            os.startsWith("Windows", ignoreCase = true) -> "windows"
            os.startsWith("Mac", ignoreCase = true) -> "osx"
            else -> "linux"
        }
        val cpu = if (arch.lowercase() in setOf("aarch64", "arm64")) "aarch64" else "x64"
        return "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-$JBR_VERSION-$platform-$cpu-$JBR_BUILD.tar.gz"
    }

    private const val JBR_VERSION = "21.0.6"
    private const val JBR_BUILD = "b895.97"

    /** "a=1; b=2" from [cookies] (already those for one URL), the most specific domain winning, like a browser sends them. */
    fun cookieHeader(cookies: List<CefCookie>): String = selectForUrl(cookies.map { CookieValue(it.name, it.value, it.domain.orEmpty()) })
        .entries.joinToString("; ") { "${it.key}=${it.value}" }

    internal data class CookieValue(val name: String, val value: String, val domain: String)

    internal fun selectForUrl(cookies: List<CookieValue>): Map<String, String> {
        val selected = linkedMapOf<String, Pair<Int, String>>()
        for (c in cookies) {
            if (c.name.isEmpty() || (c.name + c.value).any { it == ';' || it == '\r' || it == '\n' }) continue
            val weight = c.domain.removePrefix(".").length
            if (weight >= (selected[c.name]?.first ?: -1)) selected[c.name] = weight to c.value
        }
        return selected.mapValues { it.value.second }
    }
}
