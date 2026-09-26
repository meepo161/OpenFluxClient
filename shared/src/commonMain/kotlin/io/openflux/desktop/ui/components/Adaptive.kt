package io.openflux.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import io.openflux.desktop.ui.theme.AppTheme

/** The window's content size, for parts that cannot measure their parent (dialogs, scrolled pages). */
@Composable
fun windowSize(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}

/** As wide as the parent allows, but not wider than [max]. */
fun Modifier.fillUpTo(max: Dp): Modifier = widthIn(max = max).fillMaxWidth()

/** Buttons side by side that wrap to the next line when the pane is narrow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s, alignment),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        content = content,
    )
}
