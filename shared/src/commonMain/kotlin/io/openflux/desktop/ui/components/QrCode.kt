package io.openflux.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.openflux.desktop.ui.theme.AppTheme

/** A QR code on white with a quiet zone, readable in either theme. */
@Composable
fun QrCode(matrix: List<BooleanArray>, size: Dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(size)
            .clip(AppTheme.shapes.card)
            .background(Color.White)
            .padding(12.dp),
    ) {
        if (matrix.isEmpty()) return@Canvas
        val cells = matrix.size
        val cell = this.size.minDimension / cells
        matrix.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                if (dark) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
