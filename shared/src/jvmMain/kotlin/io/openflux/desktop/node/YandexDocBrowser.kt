package io.openflux.desktop.node

import io.openflux.desktop.core.DevTools
import io.openflux.desktop.core.findEdge
import io.openflux.desktop.model.NodeDocuments
import io.openflux.desktop.model.NodeWizardException
import io.openflux.desktop.model.YandexDocument
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

/**
 * Signs in to Yandex in an isolated Edge window and, as that user, creates
 * the channel's document: the /openflux folder on Disk, a new document in
 * it, and edit access for anyone with the link, which both the node and the
 * client need. It drives the same internal endpoints the Disk web client
 * calls (there is no public API for edit-by-link on personal accounts), from
 * the Disk page itself, like the Android wizard's YandexDocActivity. The
 * window runs on a throwaway profile deleted on the way out, so the login
 * stays only in the cookies handed back for the node.
 */
class YandexDocBrowser {
    @Volatile private var process: Process? = null

    suspend fun create(fileName: String, onStep: (String) -> Unit): YandexDocument {
        require(Regex("^[a-z0-9-]{1,64}$").matches(fileName)) { "Неверное имя документа" }
        cancel()
        val profile = Files.createTempDirectory("openflux-yandex-").toFile()
        val browser = ProcessBuilder(
            findEdge().absolutePath,
            "--user-data-dir=${profile.absolutePath}",
            "--remote-debugging-port=0",
            "--remote-debugging-address=127.0.0.1",
            "--remote-allow-origins=*",
            "--no-first-run",
            "--no-default-browser-check",
            "--new-window",
            "--window-size=1100,820",
            START_URL,
        ).start()
        process = browser
        try {
            onStep("Войдите в аккаунт Яндекса в открывшемся окне")
            val port = waitForPort(profile, browser)
            val deadline = System.currentTimeMillis() + SIGN_IN_TIMEOUT_MS
            while (true) {
                coroutineContext.ensureActive()
                if (!browser.isAlive) throw NodeWizardException("Окно Яндекса закрыто до создания документа")
                if (System.currentTimeMillis() > deadline) throw NodeWizardException("Время на вход в Яндекс вышло")
                val page = runCatching { DevTools.pages(port) }.getOrDefault(emptyList())
                    .firstOrNull { it.url.startsWith("https://disk.yandex.ru/client") }
                if (page == null) {
                    delay(1500)
                    continue
                }
                onStep("Создаю документ на Яндекс Диске…")
                val outcome = DevTools.connect(page).use { session ->
                    val raw = session.evaluate(script(fileName))?.jsonPrimitive?.content
                        ?: throw NodeWizardException("Страница Яндекса не ответила")
                    val result = Json.parseToJsonElement(raw).jsonObject
                    when (result["state"]?.jsonPrimitive?.content) {
                        "done" -> {
                            val url = result["url"]?.jsonPrimitive?.content?.let(NodeDocuments::clean)
                                ?: throw NodeWizardException("Яндекс вернул неожиданную ссылку на документ")
                            val cookies = session.call(
                                "Network.getCookies",
                                buildJsonObject { put("urls", buildJsonArray { add(JsonPrimitive(url)) }) },
                            )
                            YandexDocument(url, cookieHeader(cookies["cookies"]?.jsonArray?.map { it.jsonObject } ?: emptyList()))
                        }
                        "fail" -> throw NodeWizardException("Не получилось создать документ: " + (result["error"]?.jsonPrimitive?.content ?: "ошибка Яндекса"))
                        else -> null // still loading or signed out
                    }
                }
                if (outcome != null) return outcome
                onStep("Войдите в аккаунт Яндекса в открывшемся окне")
                delay(1500)
            }
        } finally {
            stop(browser)
            process = null
            deleteProfile(profile)
        }
    }

    fun cancel() {
        process?.let(::stop)
        process = null
    }

    private suspend fun waitForPort(profile: File, browser: Process): Int {
        repeat(100) {
            DevTools.port(profile)?.let { return it }
            if (!browser.isAlive) throw NodeWizardException("Edge не запустился")
            delay(100)
        }
        throw NodeWizardException("Edge не открыл отладочный порт")
    }

    private fun stop(browser: Process) {
        browser.descendants().forEach { it.destroy() }
        if (browser.isAlive) browser.destroy()
        runCatching { browser.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) }
    }

    /** Edge releases its files a moment after exit; the login must not stay on disk. */
    private suspend fun deleteProfile(profile: File) {
        repeat(20) {
            if (profile.deleteRecursively() || !profile.exists()) return
            delay(250)
        }
    }

    companion object {
        private const val START_URL = "https://disk.yandex.ru/client/disk"
        private const val SIGN_IN_TIMEOUT_MS = 15 * 60 * 1000L

        /** "a=1; b=2" from DevTools cookies, like a WebView's getCookie(url). */
        internal fun cookieHeader(cookies: List<kotlinx.serialization.json.JsonObject>): String =
            cookies.mapNotNull { c ->
                val name = c["name"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val value = c["value"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if ((name + value).any { it == ';' || it == '\r' || it == '\n' }) null else "$name=$value"
            }.distinct().joinToString("; ")

        /**
         * The same calls the Disk web client makes: page data holds the CSRF
         * keys (sk for /models-v2, skExternal for /editnew). Returns JSON:
         * {"state": "waiting" | "done" | "fail", "url", "error"}.
         */
        internal fun script(name: String): String = """
(async () => {
  const out = (o) => JSON.stringify(o);
  try {
    const el = document.getElementById('preloaded-data');
    const cfg = el ? (JSON.parse(el.textContent).config || {}) : {};
    if (!cfg.sk || !cfg.skExternal) return out({state: 'waiting'});
    const call = async (m, p) => {
      const r = await fetch('/models-v2?m=' + m, {method: 'POST', credentials: 'include',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({sk: cfg.sk, connection_id: cfg.idClient, apiMethod: m, requestParams: p})});
      const t = await r.text(); let j = null; try { j = JSON.parse(t); } catch (e) {}
      if (!r.ok || (j && j.error)) throw new Error(m + ': ' + ((j && j.error && (j.error.title || j.error.code)) || r.status));
      return j;
    };
    const info = async (path) => { const r = await call('mpfs/bulk-resource-info', {ids: [path]});
      return Array.isArray(r) && r.length ? r[0] : null; };
    const dir = await info('/disk/openflux');
    if (!dir) await call('mpfs/mkdir', {path: '/disk/openflux'});
    else if (dir.type !== 'dir') throw new Error('на Диске уже есть файл openflux, а нужна папка');
    const name = ${JsonPrimitive(name)};
    const path = '/disk/openflux/' + name + '.docx';
    let file = await info(path);
    if (!file) {
      const r = await fetch('/editnew/docx/disk/openflux?sk=' + encodeURIComponent(cfg.skExternal)
        + '&filename=' + encodeURIComponent(name), {credentials: 'include'});
      if (!r.ok) throw new Error('создание документа: ' + r.status);
      for (let i = 0; i < 30 && !file; i++) { file = await info(path); if (!file) await new Promise(r => setTimeout(r, 500)); }
    }
    if (!file || !file.meta) throw new Error('документ не появился на Диске');
    await call('mpfs/set-public', {path: path, type: 'file', allowDefaultSettingsAvailable: true});
    await call('mpfs/office-set-access-state', {resourceId: file.meta.resource_id, accessState: 'all'});
    file = await info(path);
    const url = file && file.meta && file.meta.office_online_sharing_url;
    if (!url || file.meta.office_access_state !== 'all') throw new Error('не удалось открыть редактирование по ссылке');
    return out({state: 'done', url: url});
  } catch (e) { return out({state: 'fail', error: String((e && e.message) || e)}); }
})()
""".trimIndent()
    }
}
