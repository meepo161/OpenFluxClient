package io.openflux.android

import android.content.Context
import android.net.Uri
import android.net.VpnService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What only an activity can do (system dialogs and pickers), for services
 * that outlive it. Each request waits for its result; without an activity
 * on screen it fails at once.
 */
class ActivityBridge {
    @Volatile private var activity: MainActivity? = null
    private var vpn: CompletableDeferred<Boolean>? = null
    private var pick: CompletableDeferred<Uri?>? = null
    private var scan: CompletableDeferred<String?>? = null

    fun attach(host: MainActivity) {
        activity = host
    }

    fun detach(host: MainActivity) {
        if (activity !== host) return
        activity = null
        // The activity's launchers die with it; nobody will answer these.
        vpn?.complete(false)
        pick?.complete(null)
        scan?.complete(null)
    }

    /** Android's consent to run a VPN; asks the user the first time. */
    suspend fun prepareVpn(context: Context): Boolean {
        val intent = VpnService.prepare(context) ?: return true
        val host = activity ?: return false
        val result = CompletableDeferred<Boolean>().also { vpn = it }
        withContext(Dispatchers.Main) { host.launchVpnConsent(intent) }
        return result.await()
    }

    suspend fun pickDocument(types: Array<String>): Uri? {
        val host = activity ?: return null
        val result = CompletableDeferred<Uri?>().also { pick = it }
        withContext(Dispatchers.Main) { host.launchPicker(types) }
        return result.await()
    }

    suspend fun scanQr(): String? {
        val host = activity ?: return null
        val result = CompletableDeferred<String?>().also { scan = it }
        withContext(Dispatchers.Main) { host.launchScanner() }
        return result.await()
    }

    /** Asks for the notification permission (Android 13+) without waiting. */
    fun requestNotifications() {
        activity?.let { host -> host.runOnUiThread { host.requestNotificationPermission() } }
    }

    internal fun onVpnConsent(granted: Boolean) { vpn?.complete(granted) }
    internal fun onPicked(uri: Uri?) { pick?.complete(uri) }
    internal fun onScanned(text: String?) { scan?.complete(text) }
}
