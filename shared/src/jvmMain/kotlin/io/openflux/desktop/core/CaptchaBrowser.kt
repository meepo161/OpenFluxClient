package io.openflux.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object CaptchaBrowserCookies {
    private val json = Json { ignoreUnknownKeys = true }

    fun validatedProxy(proxy: String): String {
        val host = proxy.substringBeforeLast(':')
        val port = proxy.substringAfterLast(':').toIntOrNull()
        require(host in setOf("127.0.0.1", "localhost") && port != null && port in 1..65535) {
            "Проверке ноды нужен локальный прокси"
        }
        return proxy
    }

    fun fromDevTools(response: String, url: String, nowSeconds: Long = System.currentTimeMillis() / 1000): Map<String, String> {
        val host = URI(url).host?.lowercase() ?: throw IllegalArgumentException("Invalid check URL")
        val root = json.parseToJsonElement(response).jsonObject
        val cookies = root["result"]?.jsonObject?.get("cookies")?.jsonArray
            ?: throw IllegalArgumentException("Browser did not return cookies")
        val selected = mutableMapOf<String, Pair<Int, String>>()
        for (item in cookies) {
            val cookie = item.jsonObject
            val domain = cookie["domain"]?.jsonPrimitive?.content?.removePrefix(".")?.lowercase() ?: continue
            if (host != domain && !host.endsWith(".$domain")) continue
            val expires = cookie["expires"]?.jsonPrimitive?.content?.toDoubleOrNull()
            if (expires != null && expires > 0 && expires <= nowSeconds) continue
            val name = cookie["name"]?.jsonPrimitive?.content ?: continue
            val value = cookie["value"]?.jsonPrimitive?.content ?: continue
            if (name.isEmpty()) continue
            if (domain.length >= (selected[name]?.first ?: -1)) selected[name] = domain.length to value
        }
        return selected.mapValues { it.value.second }
    }
}

/** Microsoft Edge, which the Yandex check opens in (every Windows 10/11 has it). */
internal fun findEdge(): File = listOf(
    File("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"),
    File("C:/Program Files/Microsoft/Edge/Application/msedge.exe"),
).firstOrNull(File::isFile) ?: throw IllegalStateException("Не найден Microsoft Edge: он нужен для проверки Яндекса")

/** An isolated Edge window; cookies are read only when the user submits the check. */
class CaptchaBrowser : AutoCloseable {
    private val http = HttpClient.newHttpClient()
    private var process: Process? = null
    private var profileDir: File? = null

    fun open(request: IpcCookiesRequest) {
        require(URI(request.url).scheme == "https") { "The check URL must use HTTPS" }
        close()
        val edge = findEdge()
        val appData = System.getenv("LOCALAPPDATA")?.let(::File)
            ?: File(System.getProperty("user.home"), "AppData/Local")
        val profile = File(appData, "OpenFlux/auth-browser")
        profile.mkdirs()
        val activePort = File(profile, "DevToolsActivePort")
        activePort.delete()
        val args = mutableListOf(
            edge.absolutePath,
            "--user-data-dir=${profile.absolutePath}",
            "--remote-debugging-port=0",
            "--remote-debugging-address=127.0.0.1",
            "--remote-allow-origins=*",
            "--no-first-run",
            "--new-window",
        )
        if (request.remote) {
            args += "--proxy-server=http://${CaptchaBrowserCookies.validatedProxy(request.proxy)}"
            args += "--proxy-bypass-list=<-loopback>"
        }
        args += request.url
        process = ProcessBuilder(args).start()
        profileDir = profile
    }

    fun collect(url: String): Map<String, String> {
        val profile = profileDir ?: throw IllegalStateException("Сначала откройте страницу проверки")
        val activePort = File(profile, "DevToolsActivePort")
        for (attempt in 1..50) {
            if (activePort.isFile) break
            Thread.sleep(100)
        }
        require(activePort.isFile) { "Edge не открыл отладочный порт" }
        val port = activePort.readLines().firstOrNull()?.toIntOrNull()
            ?: throw IllegalStateException("Invalid Edge debugging endpoint")
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/json/list")).GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) { "Cannot inspect Edge tabs" }
        val targets = Json.parseToJsonElement(response.body()).jsonArray
        val target = targets.firstOrNull { item ->
            val node = item.jsonObject
            node["type"]?.jsonPrimitive?.content == "page" &&
                node["url"]?.jsonPrimitive?.content?.let { runCatching { URI(it).host }.getOrNull() } != null
        }?.jsonObject ?: throw IllegalStateException("Страница проверки закрыта")
        val wsUrl = target["webSocketDebuggerUrl"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Edge did not expose the check page")
        val reply = CompletableFuture<String>()
        val listener = object : WebSocket.Listener {
            val buffer = StringBuilder()
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*>? {
                buffer.append(data)
                if (last) {
                    val message = buffer.toString()
                    buffer.setLength(0)
                    val id = runCatching { Json.parseToJsonElement(message).jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
                    if (id == "1") reply.complete(message)
                }
                webSocket.request(1)
                return null
            }
            override fun onError(webSocket: WebSocket, error: Throwable) { reply.completeExceptionally(error) }
        }
        val ws = http.newWebSocketBuilder().buildAsync(URI(wsUrl), listener).get(5, TimeUnit.SECONDS)
        try {
            ws.request(1)
            ws.sendText("""{"id":1,"method":"Network.getAllCookies"}""", true).get(5, TimeUnit.SECONDS)
            return CaptchaBrowserCookies.fromDevTools(reply.get(5, TimeUnit.SECONDS), url)
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done")
        }
    }

    override fun close() {
        process?.let { browser ->
            browser.descendants().forEach { it.destroy() }
            if (browser.isAlive) browser.destroy()
        }
        process = null
        profileDir = null
    }
}
