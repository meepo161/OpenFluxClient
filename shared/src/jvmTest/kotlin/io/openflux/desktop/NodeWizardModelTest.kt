package io.openflux.desktop

import io.openflux.desktop.data.JvmShareLinkCodec
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.CaptchaPrompt
import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.ExitAddress
import io.openflux.desktop.model.KnownServer
import io.openflux.desktop.model.LogLine
import io.openflux.desktop.model.NewChannel
import io.openflux.desktop.model.NodePlan
import io.openflux.desktop.model.NodeServers
import io.openflux.desktop.model.NodeWizardException
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.ProfileSource
import io.openflux.desktop.model.ServerProbe
import io.openflux.desktop.model.ShareConfig
import io.openflux.desktop.model.ShareTransport
import io.openflux.desktop.model.SshTarget
import io.openflux.desktop.model.TrafficStats
import io.openflux.desktop.model.TransportType
import io.openflux.desktop.service.AppContainer
import io.openflux.desktop.service.ConnectionService
import io.openflux.desktop.service.NodeWizardService
import io.openflux.desktop.service.PlatformServices
import io.openflux.desktop.service.ProfileRepository
import io.openflux.desktop.service.SettingsRepository
import io.openflux.desktop.model.NodeDocuments
import io.openflux.desktop.model.YandexDocument
import io.openflux.desktop.ui.BrowserPage
import io.openflux.desktop.ui.node.NodeWizardModel
import io.openflux.desktop.ui.node.WizardStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NodeWizardModelTest {
    private val docUrl = "https://disk.yandex.ru/edit/d/abcdefghijklmnopqrstuvwxyz"
    private val serverIp = "203.0.113.10"

    @Test
    fun wholeWizardSavesAVerifiedProfile() = runTest {
        val env = Env()
        val wizard = NodeWizardModel(env.container, this)
        wizard.host = serverIp
        wizard.password = "ssh-pass"
        wizard.connect()
        advanceUntilIdle()
        // A new server: its key must be confirmed before anything else.
        assertEquals("SHA256:new", wizard.hostKeyPrompt?.fingerprint)
        assertEquals(WizardStep.Server, wizard.step)
        wizard.trustHostKey()
        advanceUntilIdle()
        assertEquals("SHA256:new", env.settings.settings.value.knownHostKeys["$serverIp:22"])
        assertEquals(listOf(KnownServer(serverIp, 22, "root")), env.settings.settings.value.knownServers)
        assertEquals(WizardStep.Document, wizard.step)
        assertEquals("Нода $serverIp", wizard.name)

        wizard.documentInput = "$docUrl?sk=123"
        wizard.checkDocument()
        advanceUntilIdle()
        assertNull(wizard.error)
        assertEquals(WizardStep.Plan, wizard.step)
        assertEquals(docUrl, wizard.documentUrl)
        // sudo with a password: the SSH password is the likely one.
        assertTrue(wizard.needsSudoPassword)
        assertEquals("ssh-pass", wizard.sudoPassword)

        wizard.install()
        advanceUntilIdle()
        assertEquals(WizardStep.Done, wizard.step, wizard.verifyFailed ?: wizard.error ?: "")
        assertEquals(listOf("of-test12", docUrl, "31337", "ssh-pass", ""), env.node.applied)
        assertEquals(serverIp, wizard.verifiedIp)
        assertTrue(wizard.primaryUp)
        assertTrue(wizard.unsaved)

        val saved = wizard.save()!!
        assertEquals(ProfileSource.Node, saved.source)
        assertTrue(saved.session)
        assertEquals(listOf(TransportType.VYANDEX, TransportType.DIRECT), saved.carriers.map { it.type })
        assertEquals("$serverIp:31337", saved.carriers[1].value)
        assertEquals(listOf(saved), env.profiles.profiles.value)
        assertFalse(wizard.unsaved)

        wizard.close()
        assertTrue(env.node.closed)
        // The saved profile keeps its connection.
        assertTrue(env.connection.state.value is ConnectionState.Connected)
        assertEquals("", wizard.password)
    }

    @Test
    fun wrongExitAddressFailsVerification() = runTest {
        val env = Env(exitIp = "198.51.100.1")
        val wizard = NodeWizardModel(env.container, this)
        env.settings.update { it.copy(knownHostKeys = mapOf("$serverIp:22" to "SHA256:new")) }
        wizard.host = serverIp
        wizard.password = "p"
        wizard.connect()
        advanceUntilIdle()
        assertNull(wizard.hostKeyPrompt)
        wizard.documentInput = docUrl
        wizard.checkDocument()
        advanceUntilIdle()
        wizard.install()
        advanceUntilIdle()
        assertEquals(WizardStep.Verify, wizard.step)
        assertTrue(wizard.verifyFailed!!.contains("198.51.100.1"))
        // The test connection of a failed channel does not stay up.
        assertEquals(ConnectionState.Idle, env.connection.state.value)

        wizard.close()
        assertTrue(env.node.closed)
    }

    @Test
    fun wrongSudoPasswordStaysOnPlan() = runTest {
        val env = Env(sudoFails = true)
        val wizard = NodeWizardModel(env.container, this)
        env.settings.update { it.copy(knownHostKeys = mapOf("$serverIp:22" to "SHA256:new")) }
        wizard.host = serverIp
        wizard.password = "p"
        wizard.connect()
        advanceUntilIdle()
        wizard.documentInput = docUrl
        wizard.checkDocument()
        advanceUntilIdle()
        wizard.install()
        advanceUntilIdle()
        assertEquals(WizardStep.Plan, wizard.step)
        assertEquals("sudo не принял пароль", wizard.error)
        assertFalse(wizard.installed)
    }

    @Test
    fun rejectsLinksThatAreNotYandexDocuments() = runTest {
        val env = Env()
        val wizard = NodeWizardModel(env.container, this)
        env.settings.update { it.copy(knownHostKeys = mapOf("$serverIp:22" to "SHA256:new")) }
        wizard.host = serverIp
        wizard.password = "p"
        wizard.connect()
        advanceUntilIdle()
        wizard.documentInput = "https://example.com/edit/d/abcdefghijklmnopqrstuvwxyz"
        wizard.checkDocument()
        advanceUntilIdle()
        assertEquals(WizardStep.Document, wizard.step)
        assertTrue(wizard.error!!.startsWith("Нужна ссылка"))
    }

    @Test
    fun createdDocumentHandsTheSignInToTheNode() = runTest {
        val env = Env()
        val wizard = NodeWizardModel(env.container, this)
        env.settings.update { it.copy(knownHostKeys = mapOf("$serverIp:22" to "SHA256:new")) }
        wizard.host = serverIp
        wizard.password = "p"
        wizard.connect()
        advanceUntilIdle()
        wizard.createDocument()
        advanceUntilIdle()
        assertEquals("openflux-of-test12", env.node.documentName)
        assertEquals(WizardStep.Plan, wizard.step)
        assertEquals(docUrl, wizard.documentUrl)
        assertTrue(wizard.nodeSignedIn)
        assertEquals(listOf(true), env.node.plannedWithCookies)
        wizard.install()
        advanceUntilIdle()
        assertEquals("Session_id=abc; yandexuid=1", env.node.applied.last())
        assertFalse(wizard.nodeSignedIn)
    }

    @Test
    fun anotherDocumentDropsTheSignIn() = runTest {
        val env = Env()
        val wizard = NodeWizardModel(env.container, this)
        env.settings.update { it.copy(knownHostKeys = mapOf("$serverIp:22" to "SHA256:new")) }
        wizard.host = serverIp
        wizard.password = "p"
        wizard.connect()
        advanceUntilIdle()
        wizard.createDocument()
        advanceUntilIdle()
        wizard.back()
        wizard.documentInput = "https://disk.yandex.ru/edit/d/ANOTHERANOTHERANOTHER"
        wizard.checkDocument()
        advanceUntilIdle()
        assertFalse(wizard.nodeSignedIn)
        assertEquals(listOf(true, false), env.node.plannedWithCookies)
    }

    @Test
    fun servers() {
        val a = KnownServer("a", 22, "root")
        val b = KnownServer("b", 22, "root")
        assertEquals(listOf(b, a), NodeServers.remember(listOf(a), b))
        assertEquals(listOf(KnownServer("A", 22, "admin"), b), NodeServers.remember(listOf(a, b), KnownServer("A", 22, "admin")))
        assertEquals(NodeServers.MAX_KNOWN, NodeServers.remember((1..9).map { KnownServer("h$it", 22, "u") }, a).size)
        assertNull(NodeServers.port("0"))
        assertNull(NodeServers.port("70000"))
        assertEquals(2222, NodeServers.port(" 2222 "))
        assertTrue(NodeDocuments.signedIn("yandexuid=1; Session_id=abc; L=2"))
        assertFalse(NodeDocuments.signedIn("yandexuid=1; sessionid2=abc"))
    }

    private class Env(exitIp: String = "203.0.113.10", sudoFails: Boolean = false) {
        val settings = FakeSettings()
        val profiles = FakeProfiles()
        val connection = FakeConnection(exitIp)
        val node = FakeNode(sudoFails)
        val platform = FakePlatform()
        val container = AppContainer(profiles, settings, connection, platform, JvmShareLinkCodec(), node)
    }

    private class FakeNode(private val sudoFails: Boolean) : NodeWizardService {
        val applied = mutableListOf<String>()
        val plannedWithCookies = mutableListOf<Boolean>()
        var documentName = ""
        var closed = false
        private val codec = JvmShareLinkCodec()

        override suspend fun connect(target: SshTarget): ServerProbe {
            if (target.hostKey != "SHA256:new") throw NodeWizardException("новый сервер", hostKey = "SHA256:new", trust = true)
            return ServerProbe(arch = "amd64", os = "Debian 12", systemd = true, sudo = "password")
        }

        override suspend fun newChannel() = NewChannel("of-test12", "ab".repeat(32))

        override suspend fun plan(channel: String, withCookies: Boolean): NodePlan {
            plannedWithCookies += withCookies
            return NodePlan(channel = channel, port = 31337, actions = listOf("Установить ядро"))
        }

        override suspend fun apply(channel: NewChannel, documentUrl: String, port: Int, sudoPassword: String, cookieHeader: String) {
            if (sudoFails) throw NodeWizardException("sudo не принял пароль", sudo = true)
            applied += listOf(channel.id, documentUrl, port.toString(), sudoPassword, cookieHeader)
        }

        override suspend fun remove(channel: String, sudoPassword: String) = Unit
        override suspend fun checkDocument(documentUrl: String) = Unit

        override suspend fun shareLink(name: String, documentUrl: String, key: String, host: String, port: Int) =
            codec.encode(
                ShareConfig(
                    name = name, negotiate = true, secret = key, context = documentUrl,
                    transports = listOf(
                        ShareTransport(type = "vyandex", url = documentUrl, priority = 100),
                        ShareTransport(type = "direct", dial = "$host:$port", priority = 50),
                    ),
                ),
            )

        override suspend fun resolve(host: String) = setOf(host)
        override val documentPage: StateFlow<BrowserPage?> = MutableStateFlow(null)
        override suspend fun createDocument(fileName: String, onStep: (String) -> Unit): YandexDocument {
            documentName = fileName
            onStep("Войдите в аккаунт Яндекса")
            return YandexDocument("https://disk.yandex.ru/edit/d/abcdefghijklmnopqrstuvwxyz", "Session_id=abc; yandexuid=1")
        }
        override fun cancelDocument() = Unit
        override fun close() { closed = true }
    }

    private class FakeConnection(private val exitIp: String) : ConnectionService {
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
        override val traffic = MutableStateFlow(TrafficStats())
        override val exitAddress = MutableStateFlow<ExitAddress>(ExitAddress.Unknown)
        override val logs: StateFlow<List<LogLine>> = MutableStateFlow(emptyList())
        override val captcha: StateFlow<CaptchaPrompt?> = MutableStateFlow(null)
        override val exitShareLink: StateFlow<String?> = MutableStateFlow(null)
        override val socksAddress: StateFlow<String?> = MutableStateFlow(null)
        override val captchaPage: StateFlow<BrowserPage?> = MutableStateFlow(null)

        override fun connect(profile: Profile) {
            state.value = ConnectionState.Connected(profile, ConnectionMode.Client, 0)
            exitAddress.value = ExitAddress.Known(exitIp)
            traffic.value = TrafficStats(activeTransport = "vyandex", live = true)
        }

        override fun disconnect() {
            state.value = ConnectionState.Idle
            exitAddress.value = ExitAddress.Unknown
        }

        override fun refreshExitAddress() = Unit
        override fun clearLogs() = Unit
        override fun openCaptcha() = Unit
        override fun submitCaptcha() = Unit
        override fun dismissCaptcha() = Unit
        override fun shutdown() = Unit
    }

    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }

    private class FakeProfiles : ProfileRepository {
        override val profiles = MutableStateFlow<List<Profile>>(emptyList())
        private var next = 0
        override fun upsert(profile: Profile) { profiles.value = profiles.value.filterNot { it.id == profile.id } + profile }
        override fun delete(id: String) { profiles.value = profiles.value.filterNot { it.id == id } }
        override fun newId() = "p${next++}"
    }

    private class FakePlatform : PlatformServices {
        var clipboard: String? = null
        val opened = mutableListOf<String>()
        override val appVersion = "test"
        override val coreVersion = "test"
        override val systemProxySupported = false
        override fun clipboardText(): String? = clipboard
        override fun setClipboardText(text: String) { clipboard = text }
        override fun qrFromClipboardImage(): String? = null
        override fun qrFromFile(path: String): String? = null
        override fun pickFile(title: String, extensions: List<String>): String? = null
        override fun readTextFile(path: String, maxBytes: Int): String? = null
        override fun qrMatrix(text: String): List<BooleanArray> = emptyList()
        override fun openUrl(url: String) { opened += url }
        override fun newSecret() = "00".repeat(32)
        override fun now() = System.currentTimeMillis()
        override suspend fun latestRelease(): String? = null
    }
}
