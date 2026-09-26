package io.openflux.desktop.node

import io.openflux.desktop.core.CoreBinary
import io.openflux.desktop.model.NewChannel
import io.openflux.desktop.model.NodePlan
import io.openflux.desktop.model.NodeWizardException
import io.openflux.desktop.model.ServerProbe
import io.openflux.desktop.model.SshTarget
import io.openflux.desktop.service.NodeWizardService
import io.openflux.desktop.service.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetAddress

/**
 * The wizard's server side through the core's --node-wizard: one helper
 * process for the wizard's lifetime, JSON lines on stdin/stdout. Secrets go
 * only through the pipe, never on the command line or into the log.
 */
class CoreNodeWizard(
    private val settings: SettingsRepository,
    private val binary: CoreBinary,
) : NodeWizardService {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var helper: Helper? = null
    private var nextId = 0L

    private class Helper(val process: Process, val input: BufferedWriter, val output: BufferedReader)

    override suspend fun connect(target: SshTarget): ServerProbe {
        val reply = call("connect") {
            put("host", target.host)
            put("port", target.port)
            put("user", target.user)
            put("password", target.password)
            put("privateKey", target.privateKey)
            put("passphrase", target.passphrase)
            put("hostKey", target.hostKey)
        }
        return json.decodeFromJsonElement(ServerProbe.serializer(), reply.getValue("probe"))
    }

    override suspend fun newChannel(): NewChannel {
        val reply = call("newChannel") {}
        return NewChannel(reply.string("channel"), reply.string("key"))
    }

    override suspend fun plan(channel: String, withCookies: Boolean): NodePlan {
        val reply = call("plan") {
            put("channel", channel)
            put("channelPort", 0)
            put("withCookies", withCookies)
        }
        return json.decodeFromJsonElement(NodePlan.serializer(), reply.getValue("plan"))
    }

    override suspend fun apply(channel: NewChannel, documentUrl: String, port: Int, sudoPassword: String, cookieHeader: String) {
        call("apply") {
            put("channel", channel.id)
            put("key", channel.key)
            put("documentUrl", documentUrl)
            put("channelPort", port)
            put("sudoPassword", sudoPassword)
            put("cookies", cookieHeader)
        }
    }

    override suspend fun remove(channel: String, sudoPassword: String) {
        call("remove") {
            put("channel", channel)
            put("sudoPassword", sudoPassword)
        }
    }

    override suspend fun checkDocument(documentUrl: String) {
        call("checkDocument") { put("documentUrl", documentUrl) }
    }

    override suspend fun shareLink(name: String, documentUrl: String, key: String, host: String, port: Int): String =
        call("shareLink") {
            put("name", name)
            put("documentUrl", documentUrl)
            put("key", key)
            put("host", host)
            put("channelPort", port)
        }.string("link")

    override suspend fun resolve(host: String): Set<String> = withContext(Dispatchers.IO) {
        runCatching { InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.toSet() }.getOrDefault(emptySet())
    }

    override fun close() {
        val current = helper
        helper = null
        if (current != null) {
            // Closing stdin ends the helper, which closes SSH and removes the
            // downloaded installer from the server.
            runCatching { current.input.close() }
            if (!current.process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) current.process.destroyForcibly()
        }
    }

    private suspend fun call(method: String, params: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val h = helper?.takeIf { it.process.isAlive } ?: start().also { helper = it }
                val id = ++nextId
                val request = buildJsonObject {
                    put("id", id)
                    put("method", method)
                    put("params", buildJsonObject(params))
                }
                val line = try {
                    h.input.write(request.toString())
                    h.input.newLine()
                    h.input.flush()
                    h.output.readLine()
                } catch (e: java.io.IOException) {
                    null
                } ?: run {
                    helper = null
                    throw NodeWizardException("Ядро OpenFlux не ответило мастеру")
                }
                val reply = json.parseToJsonElement(line).jsonObject
                if (reply["ok"]?.jsonPrimitive?.boolean != true) {
                    throw NodeWizardException(
                        reply["error"]?.jsonPrimitive?.content ?: "Ошибка мастера",
                        hostKey = reply["hostKey"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
                        trust = reply.flag("trust"),
                        mismatch = reply.flag("mismatch"),
                        sudo = reply.flag("sudo"),
                        captcha = reply.flag("captcha"),
                    )
                }
                reply
            }
        }

    private fun start(): Helper {
        val core = binary.resolve(settings.settings.value)
            ?: throw NodeWizardException("Не найдено ядро OpenFlux: укажите его в настройках")
        val process = ProcessBuilder(core.absolutePath, "--node-wizard")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return Helper(
            process,
            process.outputStream.bufferedWriter(Charsets.UTF_8),
            process.inputStream.bufferedReader(Charsets.UTF_8),
        )
    }

    private fun JsonObject.string(name: String) =
        this[name]?.jsonPrimitive?.content ?: throw NodeWizardException("Ядро не вернуло $name")

    private fun JsonObject.flag(name: String) = this[name]?.jsonPrimitive?.booleanOrNull == true
}
