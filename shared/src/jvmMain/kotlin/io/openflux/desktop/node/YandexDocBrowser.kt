package io.openflux.desktop.node

import io.openflux.desktop.model.NodeDocuments
import io.openflux.desktop.model.NodeWizardException
import io.openflux.desktop.model.YandexDocument
import io.openflux.desktop.ui.BrowserPage
import io.openflux.desktop.web.BuiltInBrowser
import io.openflux.desktop.web.KcefPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.coroutineContext

/**
 * Signs in to Yandex in the built-in browser and, as that user, creates the
 * channel's document: the /openflux folder on Disk, a new document in it,
 * and edit access for anyone with the link, which both the node and the
 * client need. It drives the same internal endpoints the Disk web client
 * calls (there is no public API for edit-by-link on personal accounts), from
 * the Disk page itself, like the Android wizard's YandexDocActivity. The
 * browser's cookies are wiped on the way out, so the login stays only in the
 * cookies handed back for the node.
 */
class YandexDocBrowser {
    private val _page = MutableStateFlow<BrowserPage?>(null)
    val page: StateFlow<BrowserPage?> = _page.asStateFlow()

    suspend fun create(fileName: String, onStep: (String) -> Unit): YandexDocument {
        require(Regex("^[a-z0-9-]{1,64}$").matches(fileName)) { "Неверное имя документа" }
        cancel()
        BuiltInBrowser.clearCookies()
        val page = BuiltInBrowser.open(START_URL, onStep = onStep)
        _page.value = page
        try {
            onStep(SIGN_IN)
            val deadline = System.currentTimeMillis() + SIGN_IN_TIMEOUT_MS
            while (true) {
                coroutineContext.ensureActive()
                if (page.closed) throw NodeWizardException("Вход в Яндекс отменён")
                if (System.currentTimeMillis() > deadline) throw NodeWizardException("Время на вход в Яндекс вышло")
                if (!page.url.startsWith(DISK_CLIENT)) {
                    delay(1000)
                    continue
                }
                onStep("Создаю документ на Яндекс Диске…")
                val raw = runCatching { page.evaluate(script(fileName)) }.getOrNull()
                val result = raw?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                when (result?.get("state")?.jsonPrimitive?.content) {
                    "done" -> {
                        val url = result["url"]?.jsonPrimitive?.content?.let(NodeDocuments::clean)
                            ?: throw NodeWizardException("Яндекс вернул неожиданную ссылку на документ")
                        return YandexDocument(url, BuiltInBrowser.cookieHeader(BuiltInBrowser.cookies(url)))
                    }
                    "fail" -> throw NodeWizardException("Не получилось создать документ: " + (result["error"]?.jsonPrimitive?.content ?: "ошибка Яндекса"))
                    else -> Unit // still loading, signed out, or the page moved on
                }
                onStep(SIGN_IN)
                delay(1500)
            }
        } finally {
            _page.value = null
            page.close()
            BuiltInBrowser.clearCookies()
        }
    }

    fun cancel() {
        (_page.value as? KcefPage)?.close()
    }

    companion object {
        private const val DISK_CLIENT = "https://disk.yandex.ru/client"
        private const val START_URL = "https://passport.yandex.ru/auth?retpath=https%3A%2F%2Fdisk.yandex.ru%2Fclient%2Fdisk"
        private const val SIGN_IN = "Войдите в аккаунт Яндекса: документ создастся сам"
        private const val SIGN_IN_TIMEOUT_MS = 15 * 60 * 1000L

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
