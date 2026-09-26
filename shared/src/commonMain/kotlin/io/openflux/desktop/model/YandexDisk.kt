package io.openflux.desktop.model

import kotlinx.serialization.json.JsonPrimitive

/**
 * Creating the channel's document as the signed-in Yandex user, the way
 * the Disk web client does it; the built-in browser (desktop) or WebView
 * (Android) runs [script] on the Disk page.
 */
object YandexDisk {
    const val DISK_CLIENT = "https://disk.yandex.ru/client"
    const val START_URL = "https://passport.yandex.ru/auth?retpath=https%3A%2F%2Fdisk.yandex.ru%2Fclient%2Fdisk"
    const val SIGN_IN = "Войдите в аккаунт Яндекса: документ создастся сам"
    const val SIGN_IN_TIMEOUT_MS = 15 * 60 * 1000L

    /**
     * The same calls the Disk web client makes: page data holds the CSRF
     * keys (sk for /models-v2, skExternal for /editnew). Returns JSON:
     * {"state": "waiting" | "done" | "fail", "url", "error"}.
     */
    fun script(name: String): String = """
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

    /** A Yandex check or sign-in page rather than the page asked for. */
    fun isCheckpoint(url: String) = "showcaptcha" in url || "passport.yandex" in url
}
