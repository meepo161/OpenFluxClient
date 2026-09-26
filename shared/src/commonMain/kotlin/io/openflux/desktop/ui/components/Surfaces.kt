package io.openflux.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.openflux.desktop.ui.theme.AppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The Android app's card: surface fill, 1 dp border, 12 dp corners. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = AppTheme.spacing.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(AppTheme.shapes.card)
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.border, AppTheme.shapes.card)
            .padding(padding),
        content = content,
    )
}

/** Small caps section label ("АКТИВНЫЕ ПАРАМЕТРЫ"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppTheme.typography.label,
        color = AppTheme.colors.textSecondary,
        modifier = modifier,
    )
}

@Composable
fun PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.typography.pageTitle, color = AppTheme.colors.text)
            if (subtitle != null) {
                Text(subtitle, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
fun HorizontalRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.border))
}

/** "Режим — Клиент (SOCKS5)": one row of a parameters card. */
@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppTheme.colors.text,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The key keeps its words; a long value gives way and ends in "…".
        Text(
            key,
            style = AppTheme.typography.body,
            color = AppTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        Spacer(Modifier.size(AppTheme.spacing.l))
        Text(
            value,
            style = AppTheme.typography.bodyStrong,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.size(AppTheme.spacing.s))
            trailing()
        }
    }
}

/** A list of [KeyValueRow]s in one card with dividers between them. */
@Composable
fun ParamsCard(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    AppCard(modifier, padding = 0.dp) {
        rows.forEachIndexed { index, (key, value) ->
            KeyValueRow(key, value)
            if (index < rows.lastIndex) HorizontalRule()
        }
    }
}

/** An icon on a tonal square, as the Android settings list draws them. */
@Composable
fun IconBubble(resource: DrawableResource, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.accent, size: Dp = AppTheme.dimens.iconBubble) {
    Box(
        modifier.size(size).clip(AppTheme.shapes.field).background(AppTheme.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(resource), null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

enum class Tone { Neutral, Accent, Success, Warning, Danger }

@Composable
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.Neutral -> AppTheme.colors.textSecondary
    Tone.Accent -> AppTheme.colors.accent
    Tone.Success -> AppTheme.colors.success
    Tone.Warning -> AppTheme.colors.warningPressed
    Tone.Danger -> AppTheme.colors.danger
}

/** A dot and a short state word ("Подключено"). */
@Composable
fun StatusBadge(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val color = toneColor(tone)
    Row(
        modifier
            .clip(AppTheme.shapes.pill)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(6.dp))
        Text(text, style = AppTheme.typography.caption, color = color, maxLines = 1)
    }
}

/** An inline message with an optional action, for errors and hints. */
@Composable
fun Banner(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val color = toneColor(tone)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.card)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), AppTheme.shapes.card)
            .padding(horizontal = AppTheme.spacing.l, vertical = AppTheme.spacing.m),
    ) {
        // A narrow banner puts its action under the text instead of squeezing it.
        val stacked = action != null && maxWidth < 520.dp
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(AppTheme.spacing.m))
                }
                Text(text, style = AppTheme.typography.body, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
                if (action != null && !stacked) {
                    Spacer(Modifier.size(AppTheme.spacing.m))
                    action()
                }
            }
            if (action != null && stacked) {
                Box(Modifier.fillMaxWidth().padding(top = AppTheme.spacing.xs), contentAlignment = Alignment.CenterEnd) { action() }
            }
        }
    }
}

/** A centered placeholder for empty lists and panes. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    resource: DrawableResource? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.padding(AppTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
    ) {
        if (resource != null) IconBubble(resource, size = 56.dp)
        Text(title, style = AppTheme.typography.sectionTitle, color = AppTheme.colors.text, textAlign = TextAlign.Center)
        Text(
            message,
            style = AppTheme.typography.body,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(AppTheme.spacing.s))
            action()
        }
    }
}
