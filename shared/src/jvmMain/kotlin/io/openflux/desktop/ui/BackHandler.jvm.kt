package io.openflux.desktop.ui

import androidx.compose.runtime.Composable

/** The desktop has no back gesture; Esc stays with dialogs and the editor. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
