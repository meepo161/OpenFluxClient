package io.openflux.desktop.ui.home

import cafe.adriel.voyager.core.model.ScreenModel
import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.isActive
import io.openflux.desktop.model.profile
import io.openflux.desktop.service.AppContainer

/** Home actions; the screen reads the container's flows directly. */
class HomeScreenModel(private val container: AppContainer) : ScreenModel {
    val connection = container.connection
    val settings = container.settings
    val profiles = container.profiles

    /** The profile the connect button uses: the saved choice, else the first. */
    fun selectedProfile(list: List<Profile>, selectedId: String?): Profile? =
        list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()

    fun select(profile: Profile) {
        settings.update { it.copy(selectedProfileId = profile.id) }
        if (connection.state.value.isActive) connection.connect(profile)
    }

    fun toggle(profile: Profile?) {
        if (connection.state.value.isActive) {
            connection.disconnect()
        } else if (profile != null) {
            settings.update { it.copy(selectedProfileId = profile.id) }
            connection.connect(profile)
        }
    }

    fun setMode(mode: ConnectionMode) = settings.update { it.copy(mode = mode) }

    fun setSystemProxy(enabled: Boolean) = settings.update { it.copy(systemProxy = enabled) }

    /** Switching the full tunnel restarts a running connection in the new mode. */
    fun setFullTunnel(enabled: Boolean) {
        settings.update { it.copy(fullTunnel = enabled) }
        val state = connection.state.value
        if (state.isActive) state.profile?.let(connection::connect)
    }

    fun restartElevated(): Boolean = container.platform.restartElevated()

    fun copy(text: String) = container.platform.setClipboardText(text)

    fun qr(text: String) = container.platform.qrMatrix(text)
}
