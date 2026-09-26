package io.openflux.desktop.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openflux.desktop.ui.theme.AppTheme

/** Thin rounded scrollbars in the app's colours; darker while hovered. */
object DesktopScrollbars : Scrollbars {
    @Composable
    private fun style() = ScrollbarStyle(
        minimalHeight = 32.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = 250,
        unhoverColor = AppTheme.colors.textHint.copy(alpha = 0.35f),
        hoverColor = AppTheme.colors.textSecondary.copy(alpha = 0.6f),
    )

    @Composable
    override fun Vertical(state: ScrollState, modifier: Modifier) {
        VerticalScrollbar(rememberScrollbarAdapter(state), modifier.fillMaxHeight().padding(2.dp), style = style())
    }

    @Composable
    override fun Vertical(state: LazyListState, modifier: Modifier) {
        VerticalScrollbar(rememberScrollbarAdapter(state), modifier.fillMaxHeight().padding(2.dp), style = style())
    }
}
