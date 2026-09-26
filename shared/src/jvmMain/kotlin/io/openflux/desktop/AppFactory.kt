package io.openflux.desktop

import io.openflux.desktop.core.CoreBinary
import io.openflux.desktop.core.CoreConnectionService
import io.openflux.desktop.data.FileProfileRepository
import io.openflux.desktop.data.FileSettingsRepository
import io.openflux.desktop.data.JvmShareLinkCodec
import io.openflux.desktop.platform.JvmPlatformServices
import io.openflux.desktop.service.AppContainer

/** Wires the desktop implementations together; called once from main. */
fun createAppContainer(appVersion: String): AppContainer {
    val settings = FileSettingsRepository()
    val binary = CoreBinary()
    return AppContainer(
        profiles = FileProfileRepository(),
        settings = settings,
        connection = CoreConnectionService(settings, binary),
        platform = JvmPlatformServices(appVersion) { binary.version() },
        shareCodec = JvmShareLinkCodec(),
    )
}
