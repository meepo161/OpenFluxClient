package io.openflux.desktop.core

import io.openflux.desktop.data.AppDirs
import io.openflux.desktop.data.restrictToOwner
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.CaptchaPrompt
import io.openflux.desktop.ui.BrowserPage
import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.CoreConfig
import io.openflux.desktop.model.CorePaths
import io.openflux.desktop.model.CoreSource
import io.openflux.desktop.model.ExitAddress
import io.openflux.desktop.model.LogLevel
import io.openflux.desktop.model.LogLine
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.TrafficStats
import io.openflux.desktop.model.isActive
import io.openflux.desktop.platform.WindowsSystemProxy
import io.openflux.desktop.service.ConnectionService
import io.openflux.desktop.service.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs the OpenFlux core as a child process for one profile at a time and
 * turns what it reports (IPC status, captcha requests, log lines) into the
 * UI's state flows.
 */
class CoreConnectionService(
    private val settings: SettingsRepository,
    private val binary: CoreBinary,
) : ConnectionService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _traffic = MutableStateFlow(TrafficStats())
    override val traffic: StateFlow<TrafficStats> = _traffic.asStateFlow()
    private val _exitAddress = MutableStateFlow<ExitAddress>(ExitAddress.Unknown)
    override val exitAddress: StateFlow<ExitAddress> = _exitAddress.asStateFlow()
    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    override val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()
    private val _captcha = MutableStateFlow<CaptchaPrompt?>(null)
    override val captcha: StateFlow<CaptchaPrompt?> = _captcha.asStateFlow()
    private val _exitShareLink = MutableStateFlow<String?>(null)
    override val exitShareLink: StateFlow<String?> = _exitShareLink.asStateFlow()
    private val _socksAddress = MutableStateFlow<String?>(null)
    override val socksAddress: StateFlow<String?> = _socksAddress.asStateFlow()

    private val lineIds = AtomicLong()
    private val lock = Any()
    private var run: Run? = null
    private val captchaBrowser = CaptchaBrowser()
    override val captchaPage: StateFlow<BrowserPage?> = captchaBrowser.page
    private var pendingCaptcha: IpcCookiesRequest? = null

    /** One started core: its process, files and the settings it began with. */
    private class Run(
        val profile: Profile,
        val settings: AppSettings,
        val process: Process,
        val files: List<File>,
        val httpProxy: String?,
        val usesIpc: Boolean,
        val jobs: MutableList<Job> = mutableListOf(),
    ) {
        @Volatile var ipc: CoreIpc? = null
        @Volatile var stopping = false
        @Volatile var lastProblem: String? = null
        @Volatile var connectedSince: Long = 0
        /** The core printed its start banner (a fallback sign of life). */
        @Volatile var bannerSeen = false
        /** The IPC socket never answered; status comes from the log instead. */
        @Volatile var ipcUnavailable = false
    }

    init {
        // A killed app leaves its run files behind, the key among them. One
        // instance runs at a time (main holds a lock), so none are in use.
        AppDirs.runtime.listFiles()?.filter { it.name.startsWith("key-") || it.name.startsWith("profile-") }
            ?.forEach { runCatching { it.delete() } }
        // A crash while the system proxy pointed at OpenFlux leaves Windows
        // without Internet; put the saved values back on the next start.
        settings.settings.value.savedSystemProxy?.let { saved ->
            if (isWindows) runCatching { WindowsSystemProxy.restore(saved) }
            settings.update { it.copy(savedSystemProxy = null) }
        }
        scope.launch {
            settings.settings.distinctUntilChangedBy { it.systemProxy }.collect { applySystemProxy() }
        }
    }

    override fun connect(profile: Profile) {
        scope.launch {
            synchronized(lock) { run }?.let { stopRun(it, restart = true) }
            start(profile)
        }
    }

    override fun disconnect() {
        scope.launch { synchronized(lock) { run }?.let { stopRun(it, restart = false) } }
    }

    private fun start(profile: Profile) {
        val current = settings.settings.value
        _exitShareLink.value = null
        _exitAddress.value = ExitAddress.Unknown
        _traffic.value = TrafficStats()
        _captcha.value = null
        val files = mutableListOf<File>()
        try {
            val core = binary.resolve(current) ?: throw IllegalStateException(
                if (current.coreSource == CoreSource.Custom) "Файл ядра не найден: ${current.customCorePath}"
                else "Встроенное ядро не найдено. Укажите файл ядра в настройках",
            )
            val runtime = AppDirs.runtime
            val tag = profile.id.take(8)
            val keyFile = if (profile.secret.isNotEmpty()) File(runtime, "key-$tag").also {
                it.writeText(profile.secret)
                restrictToOwner(it)
                files += it
            } else null
            val confFile = File(runtime, "profile-$tag.conf")
            // AF_UNIX sockets do not work under every folder on Windows
            // (AppData\Roaming and \Local fail with EINVAL); the temp folder does.
            val ipcSocket = if (profile.session) {
                val dir = Files.createTempDirectory("openflux-ipc-").toFile()
                files += dir
                File(dir, "core.sock").also { files += it }
            } else null
            val paths = CorePaths(
                keyFile = keyFile?.absolutePath,
                confFile = confFile.absolutePath,
                cookieStore = File(AppDirs.config, "cookies/$tag.json").also { it.parentFile.mkdirs() }.absolutePath,
                ipcSocket = ipcSocket?.absolutePath,
            )
            val launch = CoreConfig.build(profile, current, paths)
            if (launch.conf != null) {
                confFile.writeText(launch.conf)
                restrictToOwner(confFile)
                files += confFile
            }
            log(LogLevel.Info, "Запуск ядра: ${profile.name} (${current.mode.label})")
            val process = ProcessBuilder(listOf(core.absolutePath) + launch.arguments)
                .directory(AppDirs.runtime)
                .redirectErrorStream(true)
                .start()
            val newRun = Run(profile, current, process, files, launch.httpProxyAddress, launch.usesIpc)
            synchronized(lock) { run = newRun }
            _socksAddress.value = launch.socksAddress
            _state.value = ConnectionState.Connecting(profile, current.mode, System.currentTimeMillis())
            newRun.jobs += scope.launch { readOutput(newRun) }
            newRun.jobs += scope.launch { awaitExit(newRun) }
            if (ipcSocket != null) newRun.jobs += scope.launch { readIpc(newRun, ipcSocket) }
        } catch (e: Exception) {
            files.sortedBy { it.isDirectory }.forEach { it.delete() }
            val message = e.message ?: "Не удалось запустить ядро"
            log(LogLevel.Error, message)
            _state.value = ConnectionState.Failed(profile, message)
        }
    }

    private fun readOutput(run: Run) {
        run.process.inputStream.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trimEnd()
                if (line.isEmpty()) continue
                val level = levelOf(line)
                log(level, line)
                friendlyProblem(line)?.let { run.lastProblem = it }
                SHARE_LINK.find(line)?.let { _exitShareLink.value = it.value }
                // Without IPC (classic profiles) the core's start banner is
                // the only sign it is up.
                if (line.contains("Running as CLIENT") || line.contains("Running as EXIT NODE")) {
                    run.bannerSeen = true
                    if (!run.usesIpc || run.ipcUnavailable) markConnected(run)
                }
                if (run.settings.mode == ConnectionMode.Exit && line.contains("Running as EXIT NODE")) markConnected(run)
            }
        }
    }

    private fun readIpc(run: Run, socket: File) {
        var lastIn = 0L
        var lastOut = 0L
        var lastAt = 0L
        val deadline = System.currentTimeMillis() + IPC_WAIT_MS
        while (run.process.isAlive && !run.stopping) {
            val ipc = runCatching { CoreIpc.connect(socket.toPath()) }.getOrNull()
            if (ipc == null) {
                if (!run.ipcUnavailable && System.currentTimeMillis() > deadline) {
                    run.ipcUnavailable = true
                    log(LogLevel.Warning, "Нет связи с ядром по IPC: статистика и проверки Яндекса недоступны, состояние берётся из журнала")
                    if (run.bannerSeen) markConnected(run)
                }
                Thread.sleep(150)
                continue
            }
            if (run.ipcUnavailable) {
                run.ipcUnavailable = false
                log(LogLevel.Info, "Связь с ядром по IPC восстановлена")
            }
            run.ipc = ipc
            try {
                ipc.readMessages { message ->
                    when (message) {
                        is IpcMessage.Status -> {
                            val status = message.status
                            val now = System.currentTimeMillis()
                            val seconds = if (lastAt == 0L) 1.0 else ((now - lastAt) / 1000.0).coerceAtLeast(0.2)
                            val up = if (lastAt == 0L) 0 else ((status.bytesOut - lastOut) / seconds).toLong().coerceAtLeast(0)
                            val down = if (lastAt == 0L) 0 else ((status.bytesIn - lastIn) / seconds).toLong().coerceAtLeast(0)
                            lastIn = status.bytesIn; lastOut = status.bytesOut; lastAt = now
                            _traffic.value = TrafficStats(up, down, status.bytesOut, status.bytesIn, status.active, live = true)
                            if (run.settings.mode == ConnectionMode.Client) {
                                if (status.connected) markConnected(run) else markReconnecting(run)
                            }
                        }
                        is IpcMessage.Cookies -> onCaptchaRequest(message.request)
                    }
                }
            } catch (e: Exception) {
                if (run.process.isAlive && !run.stopping) log(LogLevel.Warning, "IPC ядра прервался: ${e.message}")
            } finally {
                ipc.close()
                run.ipc = null
            }
        }
    }

    private fun awaitExit(run: Run) {
        val code = run.process.waitFor()
        cleanup(run)
        if (run.stopping) return
        val message = run.lastProblem ?: "Ядро остановилось (код $code)"
        log(LogLevel.Error, message)
        _state.value = ConnectionState.Failed(run.profile, message)
    }

    private fun markConnected(run: Run) {
        if (synchronized(lock) { this.run } !== run || run.stopping) return
        val first = run.connectedSince == 0L
        if (first) run.connectedSince = System.currentTimeMillis()
        val current = _state.value
        if (current !is ConnectionState.Connected) {
            _state.value = ConnectionState.Connected(run.profile, run.settings.mode, run.connectedSince)
            if (first) log(LogLevel.Success, if (run.settings.mode == ConnectionMode.Exit) "Нода запущена" else "Подключено к ноде")
            applySystemProxy()
            if (run.settings.mode == ConnectionMode.Client) refreshExitAddress()
        }
    }

    private fun markReconnecting(run: Run) {
        if (synchronized(lock) { this.run } !== run || run.stopping) return
        if (_state.value is ConnectionState.Connected) {
            _state.value = ConnectionState.Reconnecting(run.profile, run.settings.mode, run.connectedSince)
            log(LogLevel.Warning, "Связь с нодой потеряна, ядро переподключается")
        }
    }

    private suspend fun stopRun(run: Run, restart: Boolean) {
        run.stopping = true
        _state.value = ConnectionState.Disconnecting(run.profile)
        restoreSystemProxy()
        killTree(run.process)
        run.process.waitFor(5, TimeUnit.SECONDS)
        cleanup(run)
        run.jobs.forEach { it.cancel() }
        if (!restart) {
            log(LogLevel.Info, "Отключено")
            _state.value = ConnectionState.Idle
        }
        delay(100)
    }

    private fun cleanup(run: Run) {
        run.ipc?.close()
        // Files first, then the folders that held them.
        run.files.sortedBy { it.isDirectory }.forEach { it.delete() }
        synchronized(lock) { if (this.run === run) this.run = null }
        _socksAddress.value = null
        _exitAddress.value = ExitAddress.Unknown
        _traffic.value = TrafficStats()
        _captcha.value = null
        pendingCaptcha = null
        captchaBrowser.close()
        restoreSystemProxy()
    }

    private fun killTree(process: Process) {
        if (!process.isAlive) return
        runCatching {
            if (isWindows) {
                ProcessBuilder("taskkill", "/F", "/T", "/PID", process.pid().toString())
                    .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
            } else {
                process.destroy()
                if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
            }
        }.onFailure { process.destroyForcibly() }
    }

    // ---- system proxy ----

    /** Points Windows at the core while a client is connected and the setting is on. */
    @Synchronized
    private fun applySystemProxy() {
        if (!isWindows) return
        val current = synchronized(lock) { run }
        val wanted = settings.settings.value.systemProxy
        val connected = _state.value is ConnectionState.Connected || _state.value is ConnectionState.Reconnecting
        val address = current?.httpProxy
        if (!wanted || !connected || address == null || current.settings.mode != ConnectionMode.Client) {
            restoreSystemProxy()
            return
        }
        if (settings.settings.value.savedSystemProxy == null) {
            val previous = WindowsSystemProxy.read()
            settings.update { it.copy(savedSystemProxy = previous) }
        }
        runCatching { WindowsSystemProxy.enable(address) }
            .onSuccess { log(LogLevel.Info, "Системный прокси Windows: $address") }
            .onFailure { log(LogLevel.Error, it.message ?: "Не удалось включить системный прокси") }
    }

    @Synchronized
    private fun restoreSystemProxy() {
        val saved = settings.settings.value.savedSystemProxy ?: return
        runCatching { WindowsSystemProxy.restore(saved) }
            .onSuccess { log(LogLevel.Info, "Системный прокси Windows восстановлен") }
        settings.update { it.copy(savedSystemProxy = null) }
    }

    // ---- exit address ----

    override fun refreshExitAddress() {
        val checked = synchronized(lock) { run } ?: return
        val proxy = checked.httpProxy ?: return
        _exitAddress.value = ExitAddress.Checking
        scope.launch {
            val result = runCatching {
                val (host, port) = proxy.split(":").let { it[0] to it[1].toInt() }
                val client = HttpClient.newBuilder()
                    .proxy(ProxySelector.of(InetSocketAddress(host, port)))
                    .connectTimeout(Duration.ofSeconds(20))
                    .build()
                val request = HttpRequest.newBuilder(URI("https://api.ipify.org")).timeout(Duration.ofSeconds(30)).build()
                val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body().trim()
                require(IP.matches(body)) { "неожиданный ответ" }
                ExitAddress.Known(body)
            }.getOrElse { ExitAddress.Unavailable(it.message ?: "нет ответа") }
            // A disconnect during the check leaves the address unknown.
            if (synchronized(lock) { run } === checked) _exitAddress.value = result
        }
    }

    // ---- captcha ----

    private fun onCaptchaRequest(request: IpcCookiesRequest) {
        if (pendingCaptcha == request) return
        pendingCaptcha = request
        log(LogLevel.Warning, if (request.remote) "Нода просит пройти проверку Яндекса" else "Яндекс просит пройти проверку")
        _captcha.value = CaptchaPrompt(request.url, request.reason, request.remote)
        openCaptcha()
    }

    override fun openCaptcha() {
        val request = pendingCaptcha ?: return
        scope.launch {
            _captcha.update { it?.copy(error = "", progress = "Открываю страницу проверки…") }
            val error = runCatching {
                captchaBrowser.open(request) { step -> _captcha.update { it?.copy(progress = step) } }
            }.exceptionOrNull()
            _captcha.update { it?.copy(error = error?.message.orEmpty(), progress = "") }
            if (error == null && captchaBrowser.awaitPassed() && pendingCaptcha == request) {
                log(LogLevel.Info, "Страница Яндекса открылась без проверки, передаю cookies")
                submitCaptcha()
            }
        }
    }

    override fun submitCaptcha() {
        val request = pendingCaptcha ?: return
        if (_captcha.value?.busy == true) return
        _captcha.update { it?.copy(busy = true, error = "") }
        scope.launch {
            try {
                val jar = captchaBrowser.collect(request.url)
                require(jar.isNotEmpty()) { "Нет cookies для ${URI(request.url).host}: пройдите проверку на странице выше" }
                val ipc = synchronized(lock) { run }?.ipc ?: throw IllegalStateException("Ядро не на связи")
                ipc.offerCookies(IpcCookiesOffer(request.transport, jar, remote = request.remote))
                log(LogLevel.Success, "Проверка пройдена, cookies переданы ${if (request.remote) "ноде" else "ядру"}")
                pendingCaptcha = null
                _captcha.value = null
                captchaBrowser.close()
            } catch (e: Exception) {
                _captcha.update { it?.copy(busy = false, error = e.message ?: "Не удалось передать cookies") }
            }
        }
    }

    override fun dismissCaptcha() {
        pendingCaptcha = null
        _captcha.value = null
        captchaBrowser.close()
    }

    // ---- logs ----

    override fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun log(level: LogLevel, text: String) {
        val line = LogLine(lineIds.incrementAndGet(), System.currentTimeMillis(), text, level)
        _logs.update { (it + line).takeLast(MAX_LOG_LINES) }
    }

    override fun shutdown() {
        val current = synchronized(lock) { run }
        if (current != null) {
            current.stopping = true
            killTree(current.process)
            cleanup(current)
        }
        restoreSystemProxy()
        captchaBrowser.close()
    }

    companion object {
        private const val MAX_LOG_LINES = 5000
        /** How long the core has to open its IPC socket before the log takes over. */
        private const val IPC_WAIT_MS = 8000L
        private val SHARE_LINK = Regex("""openflux://v1/[A-Za-z0-9_-]+""")
        private val IP = Regex("""^[0-9a-fA-F:.]{3,45}$""")
        /** The core's --debug lines carry microseconds: "23:33:27.443294 [VOLGA]". */
        private val DEBUG_STAMP = Regex("""^\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}\.\d{6} """)

        fun levelOf(line: String): LogLevel {
            val lower = line.lowercase()
            return when {
                lower.contains("[error]") || lower.contains("fatal") || lower.contains("panic") -> LogLevel.Error
                lower.contains("warning") || lower.contains("[warn") || lower.contains("captcha required") -> LogLevel.Warning
                lower.contains("[success]") || lower.contains("running as") || lower.contains("authenticated peer") -> LogLevel.Success
                DEBUG_STAMP.containsMatchIn(line) -> LogLevel.Debug
                else -> LogLevel.Info
            }
        }

        /** A plain-language reason for common fatal core errors. */
        fun friendlyProblem(line: String): String? {
            val lower = line.lowercase()
            return when {
                lower.contains("only one usage of each socket address") || lower.contains("address already in use") ->
                    "Порт уже занят другой программой. Смените порт в настройках"
                lower.contains("read encryption key file") -> "Ядро не смогло прочитать ключ шифрования"
                lower.contains("ipc listen") -> "Ядро не смогло открыть канал связи с приложением"
                lower.contains("--config:") -> "Ядро не приняло конфигурацию: ${line.substringAfter("--config:").trim()}"
                lower.contains("failed to start transport") -> "Транспорт не запустился: ${line.substringAfter("transport:").trim().take(160)}"
                lower.contains("fatal") || lower.contains("log.fatal") -> line.substringAfter(": ").take(200)
                else -> null
            }
        }
    }
}

/** Finds the core binary: the user's file or the one shipped with the app. */
class CoreBinary {
    private val fileName: String = run {
        val os = System.getProperty("os.name").lowercase()
        val arch = when (System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "arm64"
            else -> "amd64"
        }
        when {
            os.contains("win") -> "openflux-windows-$arch.exe"
            os.contains("mac") -> "openflux-darwin-$arch"
            else -> "openflux-linux-$arch"
        }
    }

    private fun bundledCandidates(): List<File> = listOfNotNull(
        System.getProperty("compose.application.resources.dir")?.let { File(it, fileName) },
        File(System.getProperty("user.dir"), "resources/windows/$fileName"),
        File(System.getProperty("user.dir"), "desktopApp/resources/windows/$fileName"),
    )

    fun bundled(): File? = bundledCandidates().firstOrNull { it.isFile }

    fun resolve(settings: AppSettings): File? = when (settings.coreSource) {
        CoreSource.Custom -> File(settings.customCorePath.trim()).takeIf { settings.customCorePath.isNotBlank() && it.isFile }
        CoreSource.Bundled -> bundled()
    }

    /** The version file shipped next to the bundled core. */
    fun version(): String = bundled()?.let { File(it.parentFile, "openflux-core.version") }
        ?.takeIf { it.isFile }?.readText()?.trim()
        ?: if (bundled() != null) "встроенное" else "не найдено"
}
