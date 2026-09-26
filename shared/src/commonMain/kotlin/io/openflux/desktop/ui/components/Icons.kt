package io.openflux.desktop.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import io.openflux.desktop.resources.Res
import io.openflux.desktop.resources.ic_add
import io.openflux.desktop.resources.ic_apps
import io.openflux.desktop.resources.ic_arrow_back
import io.openflux.desktop.resources.ic_check
import io.openflux.desktop.resources.ic_chevron_right
import io.openflux.desktop.resources.ic_code
import io.openflux.desktop.resources.ic_dark_mode
import io.openflux.desktop.resources.ic_delete
import io.openflux.desktop.resources.ic_github
import io.openflux.desktop.resources.ic_home
import io.openflux.desktop.resources.ic_info
import io.openflux.desktop.resources.ic_key
import io.openflux.desktop.resources.ic_link
import io.openflux.desktop.resources.ic_lock
import io.openflux.desktop.resources.ic_mailru
import io.openflux.desktop.resources.ic_max
import io.openflux.desktop.resources.ic_openflux_foreground
import io.openflux.desktop.resources.ic_person
import io.openflux.desktop.resources.ic_power
import io.openflux.desktop.resources.ic_public
import io.openflux.desktop.resources.ic_qr_scan
import io.openflux.desktop.resources.ic_routing
import io.openflux.desktop.resources.ic_settings
import io.openflux.desktop.resources.ic_swap
import io.openflux.desktop.resources.ic_terminal
import io.openflux.desktop.resources.ic_visibility
import io.openflux.desktop.resources.ic_visibility_off
import io.openflux.desktop.resources.ic_yandex
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The Android app's icon set, by the names profiles store. */
object AppIcons {
    val Add = Res.drawable.ic_add
    val Apps = Res.drawable.ic_apps
    val Back = Res.drawable.ic_arrow_back
    val Check = Res.drawable.ic_check
    val ChevronRight = Res.drawable.ic_chevron_right
    val Code = Res.drawable.ic_code
    val DarkMode = Res.drawable.ic_dark_mode
    val Delete = Res.drawable.ic_delete
    val GitHub = Res.drawable.ic_github
    val Home = Res.drawable.ic_home
    val Info = Res.drawable.ic_info
    val Key = Res.drawable.ic_key
    val Link = Res.drawable.ic_link
    val Lock = Res.drawable.ic_lock
    val Logo = Res.drawable.ic_openflux_foreground
    val Person = Res.drawable.ic_person
    val Power = Res.drawable.ic_power
    val Public = Res.drawable.ic_public
    val QrScan = Res.drawable.ic_qr_scan
    val Routing = Res.drawable.ic_routing
    val Settings = Res.drawable.ic_settings
    val Swap = Res.drawable.ic_swap
    val Terminal = Res.drawable.ic_terminal
    val Visibility = Res.drawable.ic_visibility
    val VisibilityOff = Res.drawable.ic_visibility_off
    val Yandex = Res.drawable.ic_yandex

    /** A profile or transport icon by its stored name. */
    fun byName(name: String): DrawableResource = when (name) {
        "ic_link" -> Link
        "ic_lock" -> Lock
        "ic_key" -> Key
        "ic_power" -> Power
        "ic_person" -> Person
        "ic_swap" -> Swap
        "ic_terminal" -> Terminal
        "ic_apps" -> Apps
        "ic_settings" -> Settings
        "ic_yandex" -> Yandex
        "ic_mailru" -> Res.drawable.ic_mailru
        "ic_max" -> Res.drawable.ic_max
        "ic_code" -> Code
        else -> Public
    }
}

@Composable
fun AppIcon(resource: DrawableResource, tint: Color, size: Dp, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(resource), contentDescription = description, tint = tint, modifier = modifier.size(size))
}

@Composable
fun AppIcon(painter: Painter, tint: Color, size: Dp, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painter, contentDescription = description, tint = tint, modifier = modifier.size(size))
}
