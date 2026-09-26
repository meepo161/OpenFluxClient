package io.openflux.desktop.node

import io.openflux.desktop.model.NodeDocuments
import io.openflux.desktop.model.NodeWizardException
import io.openflux.desktop.model.YandexDisk
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
        val page = BuiltInBrowser.open(YandexDisk.START_URL, onStep = onStep)
        _page.value = page
        try {
            onStep(YandexDisk.SIGN_IN)
            val deadline = System.currentTimeMillis() + YandexDisk.SIGN_IN_TIMEOUT_MS
            while (true) {
                coroutineContext.ensureActive()
                if (page.closed) throw NodeWizardException("Вход в Яндекс отменён")
                if (System.currentTimeMillis() > deadline) throw NodeWizardException("Время на вход в Яндекс вышло")
                if (!page.url.startsWith(YandexDisk.DISK_CLIENT)) {
                    delay(1000)
                    continue
                }
                onStep("Создаю документ на Яндекс Диске…")
                val raw = runCatching { page.evaluate(YandexDisk.script(fileName)) }.getOrNull()
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
                onStep(YandexDisk.SIGN_IN)
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
}
