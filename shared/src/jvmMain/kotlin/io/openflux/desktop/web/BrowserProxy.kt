package io.openflux.desktop.web

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Collections

/**
 * The built-in browser's only proxy (Chromium's --proxy-server is fixed for
 * the whole process). Requests go straight out, or, while [upstream] is set,
 * through the node's HTTP proxy, so a check the node asked for is shown from
 * the node's address. Changing [upstream] drops open connections, so no
 * page keeps a tunnel that went the other way.
 */
class BrowserProxy {
    private var server: ServerSocket? = null
    private val open = Collections.synchronizedSet(mutableSetOf<Socket>())

    /** "127.0.0.1:port" of the node's proxy, null for direct. */
    @Volatile var upstream: String? = null
        set(value) {
            require(value == null || isLoopback(value)) { "Проверке ноды нужен локальный прокси" }
            if (field == value) return
            field = value
            closeConnections()
        }

    /** Starts listening on loopback; returns the port. */
    @Synchronized
    fun start(): Int {
        server?.let { return it.localPort }
        val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        server = socket
        Thread({ acceptLoop(socket) }, "openflux-browser-proxy").apply { isDaemon = true }.start()
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        closeConnections()
    }

    private fun closeConnections() {
        val sockets = synchronized(open) { open.toList().also { open.clear() } }
        sockets.forEach { runCatching { it.close() } }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: continue
            Thread({ serve(client) }, "openflux-browser-proxy-conn").apply { isDaemon = true }.start()
        }
    }

    private fun serve(client: Socket) {
        open += client
        var remote: Socket? = null
        try {
            client.soTimeout = 30_000
            val input = client.getInputStream()
            val head = readHead(input) ?: return
            val requestLine = head.substringBefore("\r\n")
            val parts = requestLine.split(' ')
            if (parts.size != 3) return
            val (method, target, version) = parts
            val via = upstream
            if (via != null) {
                // The node's proxy speaks the same protocol: pass the request on as is.
                remote = connect(via).also { open += it }
                remote.getOutputStream().apply { write(head.toByteArray(Charsets.ISO_8859_1)); flush() }
            } else if (method.equals("CONNECT", ignoreCase = true)) {
                remote = connect(target).also { open += it }
                client.getOutputStream().apply { write("$version 200 Connection Established\r\n\r\n".toByteArray()); flush() }
            } else {
                val uri = URI(target)
                if (uri.scheme != "http" || uri.host == null) return
                val port = if (uri.port > 0) uri.port else 80
                remote = connect("${uri.host}:$port").also { open += it }
                val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
                // One request per connection keeps the next one from going to the wrong host.
                val headers = head.substringAfter("\r\n").split("\r\n")
                    .filter { it.isNotEmpty() && !it.startsWith("Proxy-", true) && !it.startsWith("Connection:", true) }
                val rewritten = "$method $path $version\r\n" + headers.joinToString("") { "$it\r\n" } + "Connection: close\r\n\r\n"
                remote.getOutputStream().apply { write(rewritten.toByteArray(Charsets.ISO_8859_1)); flush() }
            }
            client.soTimeout = 0
            val upstreamSocket = remote
            val pump = Thread({ copy(upstreamSocket.getInputStream(), client.getOutputStream()); runCatching { client.shutdownOutput() } },
                "openflux-browser-proxy-down").apply { isDaemon = true; start() }
            copy(input, upstreamSocket.getOutputStream())
            runCatching { upstreamSocket.shutdownOutput() }
            pump.join()
        } catch (_: Exception) {
            // The browser retries or shows its own error page.
        } finally {
            remote?.let { open -= it; runCatching { it.close() } }
            open -= client
            runCatching { client.close() }
        }
    }

    private fun connect(address: String): Socket {
        val host = address.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        val port = address.substringAfterLast(':').toInt()
        return Socket().apply { connect(InetSocketAddress(host, port), 20_000) }
    }

    companion object {
        private const val MAX_HEAD = 64 * 1024

        fun isLoopback(address: String): Boolean {
            val host = address.substringBeforeLast(':')
            val port = address.substringAfterLast(':').toIntOrNull()
            return host in setOf("127.0.0.1", "localhost") && port != null && port in 1..65535
        }

        /** The request line and headers, up to the blank line; null if the client went away. */
        internal fun readHead(input: InputStream): String? {
            val out = ByteArrayOutputStream()
            var matched = 0
            val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
            while (out.size() < MAX_HEAD) {
                val b = input.read()
                if (b < 0) return null
                out.write(b)
                matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
                if (matched == end.size) return out.toString(Charsets.ISO_8859_1)
            }
            return null
        }

        private fun copy(from: InputStream, to: OutputStream) {
            val buffer = ByteArray(16 * 1024)
            try {
                while (true) {
                    val n = from.read(buffer)
                    if (n < 0) break
                    to.write(buffer, 0, n)
                    to.flush()
                }
            } catch (_: Exception) {
            }
        }
    }
}
