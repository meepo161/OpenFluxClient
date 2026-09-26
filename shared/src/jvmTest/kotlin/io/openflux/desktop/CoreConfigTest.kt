package io.openflux.desktop

import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.CoreConfig
import io.openflux.desktop.model.CorePaths
import io.openflux.desktop.model.ExtraTransport
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.TransportType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreConfigTest {
    private val paths = CorePaths("C:/rt/key", "C:/rt/p.conf", "C:/cfg/cookies/p.json", "C:/rt/ipc.sock")
    private val secret = "f".repeat(64)
    private val session = Profile(
        id = "p",
        name = "Nodes",
        transport = TransportType.VYANDEX,
        value = "https://disk.yandex.ru/i/one",
        secret = secret,
        session = true,
        priority = 100,
        extras = listOf(
            ExtraTransport(TransportType.VYANDEX, "https://disk.yandex.ru/i/two", priority = 90),
            ExtraTransport(TransportType.DIRECT, "203.0.113.10:8445", priority = 50),
        ),
    )

    @Test
    fun sessionClientConf() {
        val launch = CoreConfig.build(session, AppSettings(socksPort = 1090), paths)
        val conf = launch.conf!!
        assertTrue("Role = client" in conf)
        assertTrue("Socks5 = 127.0.0.1:1090" in conf)
        assertTrue("[Transport vyandex]" in conf && "[Transport vyandex-2]" in conf && "[Transport direct]" in conf)
        assertTrue("Dial = 203.0.113.10:8445" in conf)
        assertTrue("EncryptionKeyFile = C:/rt/key" in conf)
        assertFalse(secret in conf, "the key goes in its own file, not in the .conf")
        assertEquals(
            listOf(
                "--config", "C:/rt/p.conf", "--url=https://disk.yandex.ru/i/one", "--ipc-socket=C:/rt/ipc.sock",
                "--http-proxy=127.0.0.1:1091",
            ),
            launch.arguments,
        )
        assertEquals("127.0.0.1:1090", launch.socksAddress)
        assertEquals("127.0.0.1:1091", launch.httpProxyAddress)
        assertTrue(launch.usesIpc)
    }

    @Test
    fun sessionExitListensForDirect() {
        val settings = AppSettings(mode = ConnectionMode.Exit, exitDirectPort = 9000, exitShareHost = "my.host", verboseCoreLog = true)
        val launch = CoreConfig.build(session, settings, paths)
        val conf = launch.conf!!
        assertTrue("Role = exit" in conf && "Mode = l4" in conf)
        assertFalse("Socks5" in conf)
        assertFalse("Dial =" in conf)
        assertTrue("Listen = 0.0.0.0:9000" in conf)
        assertTrue(launch.arguments.containsAll(listOf("--share", "--share-host=my.host", "--debug")))
        assertFalse(launch.arguments.any { it.startsWith("--http-proxy") })
        assertNull(launch.socksAddress)
    }

    @Test
    fun classicUsesFlags() {
        val classic = Profile(id = "c", name = "Old", transport = TransportType.YANDEX, value = "https://disk.yandex.ru/i/x")
        val launch = CoreConfig.build(classic, AppSettings(), paths.copy(keyFile = null))
        assertNull(launch.conf)
        assertEquals(
            listOf(
                "--role=client", "--inbound=socks5", "--socks5=127.0.0.1:1080", "--http-proxy=127.0.0.1:1081",
                "--transport=yandex", "--codec=batched", "--url=https://disk.yandex.ru/i/x",
                "--cookie-store=C:/cfg/cookies/p.json",
            ),
            launch.arguments,
        )
        assertFalse(launch.usesIpc)
    }

    @Test
    fun refusesWhatTheCoreWouldMisread() {
        val cut = session.copy(value = "https://disk.yandex.ru/i/one#frag")
        assertFailsWith<IllegalArgumentException> { CoreConfig.build(cut, AppSettings(), paths) }
        val classicExit = Profile(id = "c", name = "Old", transport = TransportType.YANDEX, value = "https://disk.yandex.ru/i/x")
        assertFailsWith<IllegalArgumentException> { CoreConfig.build(classicExit, AppSettings(mode = ConnectionMode.Exit), paths) }
        assertFailsWith<IllegalArgumentException> { CoreConfig.build(session.copy(secret = "short"), AppSettings(), paths) }
    }
}
