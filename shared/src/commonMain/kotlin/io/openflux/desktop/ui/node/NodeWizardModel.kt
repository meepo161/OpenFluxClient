package io.openflux.desktop.ui.node

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.ExitAddress
import io.openflux.desktop.model.KnownServer
import io.openflux.desktop.model.NewChannel
import io.openflux.desktop.model.NodeDocuments
import io.openflux.desktop.model.NodePlan
import io.openflux.desktop.model.NodeServers
import io.openflux.desktop.model.NodeWizardException
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.ProfileSource
import io.openflux.desktop.model.ServerProbe
import io.openflux.desktop.model.SshTarget
import io.openflux.desktop.model.TransportType
import io.openflux.desktop.model.isActive
import io.openflux.desktop.model.profile
import io.openflux.desktop.service.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class WizardStep(val number: Int) { Server(1), Document(2), Plan(3), Verify(4), Done(4) }

/** A server key to confirm: new ([mismatch] false) or changed. */
data class HostKeyPrompt(val fingerprint: String, val mismatch: Boolean)

/**
 * "Своя нода": installs a new, independent channel on the user's VDS and
 * hands back a verified profile, like the Android NodeWizardActivity. Steps:
 * SSH to the server, the channel's Yandex document, what will change on the
 * server, install, then a real connection through the new node. Running it
 * again on the same server adds another channel next to the existing ones.
 *
 * The SSH password, private key and sudo password live only in this object
 * for the length of the wizard; the Yandex login (cookies from the built-in
 * browser) only until install.
 */
@Stable
class NodeWizardModel(private val container: AppContainer, private val scope: CoroutineScope) {
    private val service = container.nodeWizard
    private val settings = container.settings
    private val connection = container.connection

    var step by mutableStateOf(WizardStep.Server)
        private set
    /** What a running call is doing; null when idle. */
    var busy by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    // Step 1: server.
    var host by mutableStateOf("")
    var port by mutableStateOf("22")
    var user by mutableStateOf("root")
    var useKey by mutableStateOf(false)
    var password by mutableStateOf("")
    var privateKey by mutableStateOf("")
    var passphrase by mutableStateOf("")
    var hostKeyPrompt by mutableStateOf<HostKeyPrompt?>(null)
        private set
    var probe by mutableStateOf<ServerProbe?>(null)
        private set

    // Step 2: document.
    var channel by mutableStateOf<NewChannel?>(null)
        private set
    var name by mutableStateOf("")
    var documentInput by mutableStateOf("")
    var documentUrl by mutableStateOf("")
        private set
    /** Why the node, not this computer, will check the document. */
    var documentWarning by mutableStateOf("")
        private set
    /** Progress of the built-in browser while the document is being made. */
    var documentProgress by mutableStateOf<String?>(null)
        private set
    /** The Yandex sign-in for the node, dropped once it is installed. */
    private var yandexCookies = ""
    /** The document the sign-in created; another document gets none. */
    private var cookiesDocument = ""
    val nodeSignedIn: Boolean get() = yandexCookies.isNotEmpty()
    /** The Yandex page to show while the document is being made. */
    val documentPage get() = service.documentPage

    // Step 3: plan.
    var plan by mutableStateOf<NodePlan?>(null)
        private set
    var sudoPassword by mutableStateOf("")
    val needsSudoPassword: Boolean get() = probe?.sudo == "password"

    // Step 4: verification and the result.
    var installed by mutableStateOf(false)
        private set
    var shareLink by mutableStateOf("")
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    var verifiedIp by mutableStateOf("")
        private set
    var primaryUp by mutableStateOf(false)
        private set
    var verifyFailed by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf(false)
        private set
    private var stopWaitingForYandex = false

    private var job: Job? = null

    val knownServers: List<KnownServer> get() = settings.settings.value.knownServers
    /** Leaving now would leave an installed channel without a saved profile. */
    val unsaved: Boolean get() = installed && !saved

    // ---- step 1: server ----

    fun useServer(server: KnownServer) {
        host = server.host
        port = server.port.toString()
        user = server.user
        error = null
    }

    fun readKeyFile() {
        val path = container.platform.pickFile("Приватный ключ SSH", emptyList()) ?: return
        val text = container.platform.readTextFile(path)
        if (text == null) error = "Не удалось прочитать ключ: файл больше 64 КБ или недоступен"
        else { privateKey = text.trim(); error = null }
    }

    fun connect(trusted: String? = null) {
        val host = host.trim()
        val user = user.trim()
        val port = NodeServers.port(port)
        when {
            host.isEmpty() || user.isEmpty() || port == null -> { error = "Укажите адрес, порт и логин"; return }
            useKey && privateKey.isBlank() -> { error = "Вставьте приватный ключ или выберите файл"; return }
            !useKey && password.isEmpty() -> { error = "Введите пароль"; return }
        }
        val id = NodeServers.hostKeyId(host, port)
        val target = SshTarget(
            host = host, port = port, user = user,
            password = if (useKey) "" else password,
            privateKey = if (useKey) privateKey.trim() else "",
            passphrase = if (useKey) passphrase else "",
            hostKey = trusted ?: settings.settings.value.knownHostKeys[id].orEmpty(),
        )
        launchCall("Подключаюсь к серверу и проверяю его…", onFailure = { e ->
            val fingerprint = e.hostKey
            if (fingerprint != null && (e.trust || e.mismatch)) {
                hostKeyPrompt = HostKeyPrompt(fingerprint, e.mismatch)
                true
            } else false
        }) {
            probe = service.connect(target)
            settings.update { s -> s.copy(knownServers = NodeServers.remember(s.knownServers, KnownServer(host, port, user))) }
            if (channel == null) channel = service.newChannel()
            if (name.isBlank()) name = "Нода $host"
            step = WizardStep.Document
        }
    }

    fun trustHostKey() {
        val prompt = hostKeyPrompt ?: return
        hostKeyPrompt = null
        val port = NodeServers.port(port) ?: return
        val fingerprint = prompt.fingerprint
        settings.update { s -> s.copy(knownHostKeys = s.knownHostKeys + (NodeServers.hostKeyId(host, port) to fingerprint)) }
        connect(fingerprint)
    }

    fun dismissHostKey() {
        hostKeyPrompt = null
    }

    // ---- step 2: document ----

    fun createDocument() {
        val channel = channel ?: return
        launchCall("Открываю Яндекс во встроенном браузере…") {
            try {
                val document = service.createDocument("openflux-${channel.id}") { documentProgress = it }
                yandexCookies = document.cookieHeader.takeIf(NodeDocuments::signedIn).orEmpty()
                cookiesDocument = document.url
                checkNow(document.url)
            } finally {
                documentProgress = null
            }
        }
    }

    fun cancelDocument() {
        service.cancelDocument()
    }

    fun checkDocument() {
        launchCall("Проверяю документ так, как его увидит нода…") { checkNow(documentInput) }
    }

    private suspend fun checkNow(url: String) {
        val clean = NodeDocuments.clean(url) ?: throw NodeWizardException("Нужна ссылка вида https://docs.yandex.ru/edit/d/…")
        if (clean != cookiesDocument) yandexCookies = ""
        documentUrl = clean
        documentInput = clean
        busy = "Проверяю документ так, как его увидит нода…"
        documentWarning = try {
            service.checkDocument(clean)
            ""
        } catch (e: NodeWizardException) {
            // Yandex challenges this computer's address; the node checks
            // from its own, and the final verification decides.
            if (!e.captcha) throw e
            "Яндекс попросил проверку у этого компьютера, поэтому документ проверит сама нода при запуске."
        }
        busy = "Спрашиваю сервер, что изменится…"
        plan = service.plan(channel!!.id, withCookies = yandexCookies.isNotEmpty())
        if (needsSudoPassword && sudoPassword.isEmpty() && !useKey) sudoPassword = password
        step = WizardStep.Plan
    }

    fun forgetYandexSignIn() {
        yandexCookies = ""
        // The plan lists the sign-in step; ask again without it.
        launchCall("Спрашиваю сервер, что изменится…") { plan = service.plan(channel!!.id, withCookies = false) }
    }

    // ---- step 3: install ----

    fun install() {
        val channel = channel ?: return
        val plan = plan ?: return
        if (needsSudoPassword && sudoPassword.isEmpty()) { error = "Введите пароль sudo"; return }
        launchCall("Устанавливаю ноду: скачиваю ядро, пишу конфигурацию, запускаю…", onFailure = { e ->
            if (e.sudo) { error = "sudo не принял пароль"; true } else false
        }) {
            service.apply(channel, documentUrl, plan.port, sudoPassword, yandexCookies)
            installed = true
            yandexCookies = ""
            val link = service.shareLink(profileName(), documentUrl, channel.key, host.trim(), plan.port)
            shareLink = link
            val candidate = Profile.fromShare(
                container.shareCodec.decode(link), container.profiles.newId(), container.platform.now(), ProfileSource.Node,
            ).copy(icon = "ic_terminal")
            profile = candidate
            verifyNow(candidate)
        }
    }

    private fun profileName() = name.trim().ifEmpty { "Нода ${host.trim()}" }

    fun back() {
        if (busy != null) return
        error = null
        step = when (step) {
            WizardStep.Document -> WizardStep.Server
            WizardStep.Plan -> WizardStep.Document
            else -> step
        }
    }

    // ---- step 4: verification ----

    /**
     * Connects through the new node as a client and asks api.ipify.org
     * where the traffic leaves: it must be the server's address. Then waits
     * for the Yandex carrier, which may first need the node's own check
     * (the app-wide captcha dialog shows it).
     */
    fun verify() {
        val candidate = profile ?: return
        launchCall("Подключаюсь к новой ноде…") { verifyNow(candidate) }
    }

    private suspend fun verifyNow(candidate: Profile) {
        step = WizardStep.Verify
        verifyFailed = null
        verifiedIp = ""
        primaryUp = false
        stopWaitingForYandex = false
        if (settings.settings.value.mode != ConnectionMode.Client) {
            verifyFailed = "Проверка идёт в режиме клиента, а сейчас включён режим выходной ноды. Переключите режим на главной и повторите."
            return
        }
        busy = "Подключаюсь к новой ноде…"
        try {
            val expected = service.resolve(host.trim())
            connection.connect(candidate)
            val deadline = container.platform.now() + VERIFY_TIMEOUT_MS
            // connect() is asynchronous: the previous connection may still
            // show until the new one starts.
            var started = false
            var refreshes = 0
            while (verifiedIp.isEmpty()) {
                if (stopWaitingForYandex) throw NodeWizardException("Проверка остановлена: нода пока не ответила")
                if (container.platform.now() > deadline) {
                    throw NodeWizardException("Нода не ответила: проверьте, что сервер доступен, а документ и ключ совпадают")
                }
                val state = connection.state.value
                if (state.profile?.id == candidate.id) started = true
                else if (started) throw NodeWizardException("Проверку прервало отключение или другое подключение")
                when {
                    !started -> Unit
                    state is ConnectionState.Failed -> throw NodeWizardException(state.message)
                    state is ConnectionState.Connected -> {
                        busy = "Канал поднялся, открываю сайт через него…"
                        when (val address = connection.exitAddress.value) {
                            is ExitAddress.Known -> {
                                if (expected.isNotEmpty() && address.ip !in expected) {
                                    throw NodeWizardException("Запрос вышел с адреса ${address.ip}, а не с адреса сервера")
                                }
                                verifiedIp = address.ip
                            }
                            is ExitAddress.Unavailable -> {
                                if (refreshes++ >= 3) throw NodeWizardException("Канал поднялся, но запрос через него не прошёл: ${address.reason}")
                                delay(2000)
                                connection.refreshExitAddress()
                            }
                            else -> Unit
                        }
                    }
                }
                if (verifiedIp.isEmpty()) delay(POLL_MS)
            }
            // Traffic may have gone through the direct backup. Give the
            // Yandex carrier the rest of the time to come up: the node may
            // first need its own check, which the app-wide captcha dialog shows.
            busy = "Жду канал через Яндекс…"
            while (!stopWaitingForYandex && container.platform.now() < deadline && !yandexUp()) {
                if (connection.state.value.profile?.id != candidate.id) break
                delay(POLL_MS)
            }
            primaryUp = yandexUp()
            step = WizardStep.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val state = connection.state.value
            if (state.profile?.id == candidate.id && state.isActive) connection.disconnect()
            verifyFailed = e.message ?: "Проверка не прошла"
        }
    }

    private fun yandexUp() = connection.traffic.value.activeTransport == TransportType.VYANDEX.cliName

    /** Stops waiting for Yandex and keeps what was proven so far. */
    fun stopWaiting() {
        stopWaitingForYandex = true
    }

    fun skipVerification() {
        verifyFailed = null
        step = WizardStep.Done
    }

    fun removeChannel() {
        val channel = channel ?: return
        launchCall("Удаляю канал с сервера…", onFailure = { e ->
            if (e.sudo) { error = "sudo не принял пароль"; true } else false
        }) {
            service.remove(channel.id, sudoPassword)
            installed = false
            notice = "Канал ${channel.id} удалён с сервера"
        }
    }

    // ---- done ----

    /** Saves the profile here; returns it, or null when there is none. */
    fun save(): Profile? {
        val candidate = profile ?: return null
        val named = candidate.copy(name = profileName())
        container.profiles.upsert(named)
        profile = named
        saved = true
        settings.update { it.copy(selectedProfileId = named.id) }
        return named
    }

    fun copyLink() = container.platform.setClipboardText(shareLink)



    fun qr() = container.platform.qrMatrix(shareLink)

    /** Ends SSH and the Yandex window; drops the test connection of an unsaved profile. */
    fun close() {
        job?.cancel()
        service.cancelDocument()
        service.close()
        val state = connection.state.value
        if (!saved && profile != null && state.profile?.id == profile?.id && state.isActive) connection.disconnect()
        password = ""
        privateKey = ""
        passphrase = ""
        sudoPassword = ""
        yandexCookies = ""
    }

    /**
     * Runs one wizard call at a time. A [NodeWizardException] becomes [error]
     * unless [onFailure] handled it (returned true).
     */
    private fun launchCall(
        message: String,
        onFailure: (NodeWizardException) -> Boolean = { false },
        block: suspend () -> Unit,
    ) {
        if (busy != null) return
        busy = message
        error = null
        notice = null
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NodeWizardException) {
                if (!onFailure(e)) error = e.message
            } catch (e: Exception) {
                error = e.message ?: "Ошибка мастера"
            } finally {
                busy = null
            }
        }
    }

    companion object {
        private const val VERIFY_TIMEOUT_MS = 150_000L
        private const val POLL_MS = 400L
    }
}
