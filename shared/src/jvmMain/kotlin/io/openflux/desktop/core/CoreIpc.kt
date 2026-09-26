package io.openflux.desktop.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Path

/** The core asks for a passed Yandex check (MsgCookiesRequest). */
@Serializable
data class IpcCookiesRequest(
    val transport: String,
    val url: String,
    val reason: String = "",
    val remote: Boolean = false,
    val proxy: String = "",
)

/** Cookies for the core (MsgCookiesOffer); remote ones go on to the exit. */
@Serializable
data class IpcCookiesOffer(
    val transport: String,
    val jar: Map<String, String>,
    val domain: String = "",
    val remote: Boolean = false,
)

/** The core's once-a-second report (MsgStatus). */
@Serializable
data class IpcStatus(
    val running: Boolean = false,
    val connected: Boolean = false,
    @SerialName("bytes_in") val bytesIn: Long = 0,
    @SerialName("bytes_out") val bytesOut: Long = 0,
    @SerialName("uptime_ms") val uptimeMs: Long = 0,
    val active: String = "",
)

sealed interface IpcMessage {
    data class Cookies(val request: IpcCookiesRequest) : IpcMessage
    data class Status(val status: IpcStatus) : IpcMessage
}

/**
 * Frames on the core's IPC socket: a big-endian uint32 length, then one
 * type byte and a JSON body (transport/ipc in the core).
 */
object IpcCodec {
    const val COOKIES_REQUEST = 1
    const val COOKIES_OFFER = 2
    const val STATUS = 3
    private const val MAX_FRAME_BYTES = 1 shl 20
    val json = Json { ignoreUnknownKeys = true }

    fun encodeFrame(type: Int, payload: String): ByteArray {
        val body = payload.toByteArray(Charsets.UTF_8)
        require(body.size + 1 <= MAX_FRAME_BYTES) { "IPC frame too large" }
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(body.size + 1)
                out.writeByte(type)
                out.write(body)
            }
        }.toByteArray()
    }

    /** The next message the app cares about, null for other frame types. */
    fun readMessage(input: InputStream): IpcMessage? {
        val data = DataInputStream(input)
        val length = data.readInt()
        require(length in 1..MAX_FRAME_BYTES) { "Invalid IPC frame length: $length" }
        val type = data.readUnsignedByte()
        val body = ByteArray(length - 1).also(data::readFully).toString(Charsets.UTF_8)
        return when (type) {
            COOKIES_REQUEST -> IpcMessage.Cookies(json.decodeFromString(IpcCookiesRequest.serializer(), body))
            STATUS -> IpcMessage.Status(json.decodeFromString(IpcStatus.serializer(), body))
            else -> null
        }
    }

    fun encodeOffer(offer: IpcCookiesOffer): ByteArray =
        encodeFrame(COOKIES_OFFER, json.encodeToString(IpcCookiesOffer.serializer(), offer))
}

class CoreIpc private constructor(private val channel: SocketChannel) : AutoCloseable {
    private val input = Channels.newInputStream(channel)
    private val output = Channels.newOutputStream(channel)

    /** Reads until the core closes the socket. */
    fun readMessages(onMessage: (IpcMessage) -> Unit) {
        while (channel.isOpen) {
            val message = try {
                IpcCodec.readMessage(input)
            } catch (_: EOFException) {
                return
            }
            if (message != null) onMessage(message)
        }
    }

    @Synchronized
    fun offerCookies(offer: IpcCookiesOffer) {
        output.write(IpcCodec.encodeOffer(offer))
        output.flush()
    }

    override fun close() = channel.close()

    companion object {
        fun connect(path: Path): CoreIpc {
            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            try {
                channel.connect(UnixDomainSocketAddress.of(path))
                return CoreIpc(channel)
            } catch (e: Exception) {
                channel.close()
                throw e
            }
        }
    }
}
