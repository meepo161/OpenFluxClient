package io.openflux.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Microsoft Edge, which the Yandex windows open in (every Windows 10/11 has it). */
internal fun findEdge(): File = listOf(
    File("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"),
    File("C:/Program Files/Microsoft/Edge/Application/msedge.exe"),
).firstOrNull(File::isFile) ?: throw IllegalStateException("Не найден Microsoft Edge: он нужен для входа в Яндекс")

/** One page target of a browser started with --remote-debugging-port. */
internal data class DevToolsTarget(val url: String, val webSocketUrl: String)

internal object DevTools {
    private val http: HttpClient = HttpClient.newHttpClient()

    /** The debugging port Edge wrote into its profile, null until it is up. */
    fun port(profile: File): Int? = File(profile, "DevToolsActivePort").takeIf(File::isFile)
        ?.readLines()?.firstOrNull()?.trim()?.toIntOrNull()

    fun pages(port: Int): List<DevToolsTarget> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/json/list")).GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return emptyList()
        return Json.parseToJsonElement(response.body()).jsonArray.mapNotNull { item ->
            val node = item.jsonObject
            if (node["type"]?.jsonPrimitive?.content != "page") return@mapNotNull null
            val url = node["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val ws = node["webSocketDebuggerUrl"]?.jsonPrimitive?.content ?: return@mapNotNull null
            DevToolsTarget(url, ws)
        }
    }

    /** Opens a DevTools session on [target]; close it when done. */
    fun connect(target: DevToolsTarget): DevToolsSession = DevToolsSession(http, target.webSocketUrl)
}

/** Request/response calls over one page's DevTools WebSocket. */
internal class DevToolsSession(http: HttpClient, url: String) : AutoCloseable {
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()
    private val socket: WebSocket

    init {
        val listener = object : WebSocket.Listener {
            val buffer = StringBuilder()
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*>? {
                buffer.append(data)
                if (last) {
                    val message = runCatching { Json.parseToJsonElement(buffer.toString()).jsonObject }.getOrNull()
                    buffer.setLength(0)
                    val id = message?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
                    if (id != null) pending.remove(id)?.complete(message)
                }
                webSocket.request(1)
                return null
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                pending.values.forEach { it.completeExceptionally(error) }
            }
        }
        socket = http.newWebSocketBuilder().buildAsync(URI(url), listener).get(5, TimeUnit.SECONDS)
        socket.request(1)
    }

    fun call(method: String, params: JsonObject = JsonObject(emptyMap()), timeoutSeconds: Long = 30): JsonObject {
        val id = ids.incrementAndGet()
        val reply = CompletableFuture<JsonObject>()
        pending[id] = reply
        val message = buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        }
        socket.sendText(message.toString(), true).get(5, TimeUnit.SECONDS)
        val response = try {
            reply.get(timeoutSeconds, TimeUnit.SECONDS)
        } finally {
            pending.remove(id)
        }
        response["error"]?.let { throw IllegalStateException("DevTools $method: $it") }
        return response["result"]?.jsonObject ?: JsonObject(emptyMap())
    }

    /** Runs [expression] in the page, awaiting a promise; returns its JSON value. */
    fun evaluate(expression: String, timeoutSeconds: Long = 90): JsonElement? {
        val result = call(
            "Runtime.evaluate",
            buildJsonObject {
                put("expression", expression)
                put("awaitPromise", true)
                put("returnByValue", true)
            },
            timeoutSeconds,
        )
        result["exceptionDetails"]?.let { throw IllegalStateException("Скрипт на странице Яндекса упал") }
        return result["result"]?.jsonObject?.get("value")
    }

    override fun close() {
        runCatching { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done") }
    }
}
