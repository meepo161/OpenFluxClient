package io.openflux.desktop

import io.openflux.desktop.core.IpcCodec
import io.openflux.desktop.core.IpcCookiesOffer
import io.openflux.desktop.core.IpcMessage
import io.openflux.desktop.core.IpcStatus
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IpcCodecTest {
    @Test
    fun readsCoreStatusFrames() {
        // What the core's ipcStatusLoop writes, plus a field from some future version.
        val body = """{"running":true,"connected":true,"bytes_in":1234,"bytes_out":56,"uptime_ms":7000,"active":"vyandex-2","future":1}"""
        val frame = IpcCodec.encodeFrame(IpcCodec.STATUS, body)
        assertEquals(body.toByteArray().size + 1, DataInputStream(ByteArrayInputStream(frame)).readInt())
        val message = IpcCodec.readMessage(ByteArrayInputStream(frame))
        assertEquals(IpcMessage.Status(IpcStatus(true, true, 1234, 56, 7000, "vyandex-2")), message)
    }

    @Test
    fun readsCookieRequestsAndSkipsUnknown() {
        val request = IpcCodec.encodeFrame(IpcCodec.COOKIES_REQUEST, """{"transport":"vyandex","url":"https://ya.ru","remote":true}""")
        val unknown = IpcCodec.encodeFrame(99, "{}")
        val input = ByteArrayInputStream(unknown + request)
        assertNull(IpcCodec.readMessage(input))
        val cookies = IpcCodec.readMessage(input) as IpcMessage.Cookies
        assertEquals("vyandex", cookies.request.transport)
        assertEquals(true, cookies.request.remote)
    }

    @Test
    fun encodesOffers() {
        val frame = IpcCodec.encodeOffer(IpcCookiesOffer("vyandex", mapOf("Session_id" to "x"), ".yandex.ru", remote = true))
        val data = DataInputStream(ByteArrayInputStream(frame))
        val length = data.readInt()
        assertEquals(IpcCodec.COOKIES_OFFER, data.readUnsignedByte())
        val json = ByteArray(length - 1).also(data::readFully).decodeToString()
        assertEquals("""{"transport":"vyandex","jar":{"Session_id":"x"},"domain":".yandex.ru","remote":true}""", json)
    }

    @Test
    fun rejectsOversizedFrames() {
        val bogus = byteArrayOf(0x7f, 0, 0, 0, 3)
        assertFailsWith<IllegalArgumentException> { IpcCodec.readMessage(ByteArrayInputStream(bogus)) }
    }
}
