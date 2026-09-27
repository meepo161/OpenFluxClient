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
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.callback.CefCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandler
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import org.cef.security.CefSSLInfo
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
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/** A page open in the built-in browser. Close it when done. */
class KcefPage internal constructor(private val browser: KCEFBrowser) : BrowserPage {
    /** The browser's native view, for a SwingPanel. */
    val component: Component get() = browser.uiComponent

    val url: String get() = browser.url.orEmpty()

    val loading: Boolean get() = runCatching { browser.isLoading }.getOrDefault(true)

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
            return withTimeoutOrNull(timeoutMs) { result.await() }
                ?: run {
                    BrowserLog.problem("скрипт на ${BrowserLog.short(url)} не ответил за ${timeoutMs / 1000} с (загружается: $loading)")
                    throw IllegalStateException("Страница не ответила")
                }
        } finally {
            BuiltInBrowser.pending.remove(id)
        }
    }

    /**
     * Closing a browser makes JCEF (CefBrowser_N.doClose) send WINDOW_CLOSING
     * to the window its view sits in: meant for a browser in a frame of its
     * own, here it is the OpenFlux window, which then went to the tray (or
     * quit, without "close to tray") as soon as a wizard step or a check was
     * done. So the view leaves the window first, on the UI thread, where
     * JCEF looks for that window.
     */
    fun close() {
        if (closed) return
        closed = true
        val dispose = {
            val view = browser.uiComponent
            view.parent?.let { parent ->
                parent.remove(view)
                parent.revalidate()
            }
            runCatching { browser.dispose() }
            Unit
        }
        if (SwingUtilities.isEventDispatchThread()) dispose() else SwingUtilities.invokeLater(dispose)
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
    @Volatile private var client: KCEFClient? = null
    /** KCEF gets stuck "initializing" when its start fails; this process cannot start it again. */
    @Volatile private var broken: String? = null

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
            // Off-screen: frames are drawn by OsrView, see there why.
            val view = OsrView()
            val browser = client.createBrowser(url, CefRendering.CefRenderingWithHandler(view.renderHandler, view), false)
            view.browser = browser
            // Create it now, not when shown: scripts and cookies work before the page is on screen.
            browser.createImmediately()
            BrowserLog.info("открываю ${BrowserLog.short(url)}" + if (upstream != null) " через прокси ноды $upstream" else " напрямую")
            KcefPage(browser)
        }
    }

    /** Cookies the browser would send to [url], HTTP-only ones included. */
    suspend fun cookies(url: String): List<CefCookie> {
        // Any CEF call before KCEF starts it would start CEF itself with
        // the wrong settings, and KCEF could not start it afterwards.
        if (client == null) return emptyList()
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
        if (client == null) return // nothing to forget, and see cookies()
        runCatching { CefCookieManager.getGlobalManager().deleteCookies(null, null) }
    }

    /** Back to direct traffic once a page that used the node's proxy is closed. */
    fun release() {
        proxy.upstream = null
    }

    private suspend fun client(onStep: (String) -> Unit): KCEFClient = lock.withLock {
        client?.let { return it }
        broken?.let { throw IllegalStateException(it) }
        try {
            start(onStep).also { client = it }
        } catch (e: Exception) {
            val message = e.message ?: "Встроенный браузер не запустился"
            // A failed download can be tried again; a start that got CEF half up cannot.
            if (runCatching { CefApp.getInstanceIfAny() }.getOrNull() == null) throw e
            val final = if ("перезапустите" in message.lowercase()) message else "$message. Перезапустите OpenFlux и повторите"
            broken = final
            throw IllegalStateException(final, e)
        }
    }

    private suspend fun start(onStep: (String) -> Unit): KCEFClient {
        val port = proxy.start()
        val initialized = CompletableDeferred<Unit>()
        var last = ""
        val step = { text: String -> if (text != last) { last = text; BrowserLog.info(text); onStep(text) } }
        val dir = installDir
        BrowserLog.info(
            "запуск: папка $dir, установлен ${File(dir, "install.lock").isFile}, пакет ${packageUrl()}, " +
                "java ${System.getProperty("java.version")} (${System.getProperty("java.home")}), ${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
        )
        runCatching { BrowserLog.cefLogFile.delete() }
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
                        windowlessRenderingEnabled = true
                        // The core's own (transport/yandex volgaUserAgent), like the
                        // Android app's WebViews: Yandex ties a passed check to it.
                        userAgent = YANDEX_USER_AGENT
                        // JCEF's defaults point into the running JVM's java.home; empty
                        // paths make KCEF use the downloaded runtime instead.
                        resourcesDirPath = null
                        localesDirPath = null
                        browserSubProcessPath = helper(dir)
                        logFile = BrowserLog.cefLogFile.absolutePath
                        logSeverity = dev.datlag.kcef.KCEFBuilder.Settings.LogSeverity.Info
                    }
                    // Our own list, not JCEF's defaults (which pin the scale factor to 1 and
                    // blur HiDPI screens). No GPU: a sign-in page does not need it, and a
                    // bad driver would take the browser down.
                    val switches = arrayOf(
                        "--disable-features=SpareRendererForSitePerProcess",
                        "--disable-gpu",
                        "--proxy-server=http://127.0.0.1:$port",
                        "--proxy-bypass-list=<-loopback>",
                    )
                    args(*switches)
                    // KCEF hands args() only to CefApp.startup; Chromium reads its
                    // switches from the app handler, which KCEF builds with none. Without
                    // this the pages went through the system proxy, with the GPU on.
                    appHandler(KCEF.AppHandler(switches))
                    BrowserLog.info("ключи Chromium: ${switches.joinToString(" ")}")
                },
                onError = { error = it; BrowserLog.problem("ошибка запуска: $it") },
                onRestartRequired = { restart = true; BrowserLog.problem("KCEF просит перезапуск") },
            )
        }
        BrowserLog.info("файлы: " + listOf("jcef_helper.exe", "jcef_helper", "libcef.dll", "libcef.so", "jcef.dll", "icudtl.dat", "resources.pak", "locales")
            .filter { File(dir, it).exists() }.joinToString())
        if (restart) throw IllegalStateException("Встроенный браузер скачан: перезапустите OpenFlux и повторите")
        error?.let { throw IllegalStateException("Встроенный браузер не запустился: ${it.message ?: it::class.simpleName}") }
        withTimeoutOrNull(START_TIMEOUT_MS) { initialized.await() }
            ?: throw IllegalStateException("Встроенный браузер не запустился за минуту")
        val created = withTimeoutOrNull(START_TIMEOUT_MS) { KCEF.newClient() }
            ?: throw IllegalStateException("Встроенный браузер не ответил")
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
        watch(created)
        BrowserLog.info("браузер готов: ${runCatching { CefApp.getInstance().version?.toString()?.replace(Regex("\\s+"), " ") }.getOrNull() ?: "?"}")
        return created
    }

    /** Page events for [BrowserLog]: a white page is a load error or a dead renderer. */
    private fun watch(client: KCEFClient) {
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, type: CefRequest.TransitionType?) {
                if (frame?.isMain == true) BrowserLog.info("страница ${browser?.identifier}: загружаю ${BrowserLog.short(frame.url)}")
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) BrowserLog.info("страница ${browser?.identifier}: загружена, HTTP $httpStatusCode, ${BrowserLog.short(frame.url)}")
            }

            override fun onLoadError(browser: CefBrowser?, frame: CefFrame?, errorCode: CefLoadHandler.ErrorCode?, errorText: String?, failedUrl: String?) {
                BrowserLog.problem("страница ${browser?.identifier}: ошибка загрузки $errorCode $errorText, ${BrowserLog.short(failedUrl)}")
            }
        })
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {
                if (frame?.isMain == true) BrowserLog.info("страница ${browser?.identifier}: адрес ${BrowserLog.short(url)}")
            }

            override fun onConsoleMessage(browser: CefBrowser?, level: CefSettings.LogSeverity?, message: String?, source: String?, line: Int): Boolean {
                if (level != null && level >= CefSettings.LogSeverity.LOGSEVERITY_WARNING) {
                    BrowserLog.problem("страница ${browser?.identifier}: консоль: ${message.orEmpty().take(300)} (${BrowserLog.short(source)}:$line)")
                }
                return false
            }
        })
        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onRenderProcessTerminated(browser: CefBrowser?, status: CefRequestHandler.TerminationStatus?) {
                BrowserLog.problem("страница ${browser?.identifier}: процесс отрисовки завершился: $status")
            }

            override fun onCertificateError(browser: CefBrowser?, cert_error: CefLoadHandler.ErrorCode?, request_url: String?, sslInfo: CefSSLInfo?, callback: CefCallback?): Boolean {
                BrowserLog.problem("страница ${browser?.identifier}: ошибка сертификата $cert_error, ${BrowserLog.short(request_url)}")
                return false
            }
        })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser?) {
                BrowserLog.info("страница ${browser?.identifier}: создана")
            }
        })
    }

    /** jcef_helper next to libcef; on Windows with its .exe, which KCEF leaves out. */
    private fun helper(dir: File): String? =
        listOf("jcef_helper.exe", "jcef_helper").map { File(dir, it) }.firstOrNull(File::isFile)?.absolutePath

    private const val START_TIMEOUT_MS = 60_000L
    const val YANDEX_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0"

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
