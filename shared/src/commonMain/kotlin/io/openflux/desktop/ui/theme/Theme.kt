package io.openflux.desktop.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The OpenFlux palette, taken from the Android app: the same accent, the
 * same neutral surfaces in light and dark, and the same status colors.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    /** Hover and selected rows; a tonal step above [surface]. */
    val surfaceTonal: Color,
    val sidebar: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    val textHint: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val warningPressed: Color,
    val danger: Color,
    val dangerPressed: Color,
    val dangerSoft: Color,
    val logText: Color,
    val scrim: Color,
)

val LightColors = AppColors(
    isDark = false,
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceTonal = Color(0xFFEEF2FF),
    sidebar = Color(0xFFF1F3F6),
    border = Color(0xFFDADCE0),
    text = Color(0xFF202124),
    textSecondary = Color(0xFF5F6368),
    textHint = Color(0xFF80868B),
    accent = Color(0xFF4F7CFF),
    accentPressed = Color(0xFF3B5DBF),
    accentSoft = Color(0x1F4F7CFF),
    onAccent = Color.White,
    success = Color(0xFF1E8E3E),
    warning = Color(0xFFFBBF24),
    warningPressed = Color(0xFFD97706),
    danger = Color(0xFFD93025),
    dangerPressed = Color(0xFFB91C1C),
    dangerSoft = Color(0x14D93025),
    logText = Color(0xFF3C4043),
    scrim = Color(0x66000000),
)

val DarkColors = AppColors(
    isDark = true,
    background = Color(0xFF080B12),
    surface = Color(0xFF131722),
    surfaceTonal = Color(0xFF1B2233),
    sidebar = Color(0xFF0D1119),
    border = Color(0xFF2A3446),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8A92A6),
    textHint = Color(0xFF5A6272),
    accent = Color(0xFF4F7CFF),
    accentPressed = Color(0xFF3B5DBF),
    accentSoft = Color(0x294F7CFF),
    onAccent = Color.White,
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    warningPressed = Color(0xFFD97706),
    danger = Color(0xFFF28B82),
    dangerPressed = Color(0xFFEF4444),
    dangerSoft = Color(0x1FF28B82),
    logText = Color(0xFFDADCE0),
    scrim = Color(0x99000000),
)

/** Type scale: Android's sizes, one step denser for a desktop window. */
@Immutable
data class AppTypography(
    val pageTitle: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    val sectionTitle: TextStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    val label: TextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp),
    val body: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    val bodyStrong: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val bodySmall: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    val caption: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
    val metric: TextStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    val mono: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
)

@Immutable
data class AppSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    /** Side margin of a page, as on Android. */
    val page: Dp = 24.dp,
)

@Immutable
data class AppShapes(
    val small: RoundedCornerShape = RoundedCornerShape(8.dp),
    val field: RoundedCornerShape = RoundedCornerShape(10.dp),
    val card: RoundedCornerShape = RoundedCornerShape(12.dp),
    val button: RoundedCornerShape = RoundedCornerShape(12.dp),
    val dialog: RoundedCornerShape = RoundedCornerShape(16.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(50),
)

@Immutable
data class AppDimens(
    val sidebarWidth: Dp = 232.dp,
    val sidebarCompactWidth: Dp = 72.dp,
    val buttonHeight: Dp = 40.dp,
    val fieldHeight: Dp = 44.dp,
    val iconButton: Dp = 36.dp,
    val icon: Dp = 20.dp,
    val iconBubble: Dp = 36.dp,
    val listRow: Dp = 60.dp,
    val connectButton: Dp = 150.dp,
    val connectRingOuter: Dp = 214.dp,
    val connectRingInner: Dp = 182.dp,
    val masterPaneWidth: Dp = 340.dp,
    val settingsNavWidth: Dp = 240.dp,
    val dialogWidth: Dp = 480.dp,
)

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf { AppTypography() }
private val LocalSpacing = staticCompositionLocalOf { AppSpacing() }
private val LocalShapes = staticCompositionLocalOf { AppShapes() }
private val LocalDimens = staticCompositionLocalOf { AppDimens() }

/** Access point for the design system: `AppTheme.colors.accent`. */
object AppTheme {
    val colors: AppColors @Composable get() = LocalColors.current
    val typography: AppTypography @Composable get() = LocalTypography.current
    val spacing: AppSpacing @Composable get() = LocalSpacing.current
    val shapes: AppShapes @Composable get() = LocalShapes.current
    val dimens: AppDimens @Composable get() = LocalDimens.current
}

@Composable
fun OpenFluxTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val material = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent, background = colors.background,
            onBackground = colors.text, surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceTonal, onSurfaceVariant = colors.textSecondary,
            outline = colors.border, error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent, background = colors.background,
            onBackground = colors.text, surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceTonal, onSurfaceVariant = colors.textSecondary,
            outline = colors.border, error = colors.danger,
        )
    }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides AppTypography(),
        LocalSpacing provides AppSpacing(),
        LocalShapes provides AppShapes(),
        LocalDimens provides AppDimens(),
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
