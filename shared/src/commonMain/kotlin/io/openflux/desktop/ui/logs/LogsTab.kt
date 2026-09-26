package io.openflux.desktop.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.openflux.desktop.model.LogLevel
import io.openflux.desktop.model.LogLine
import io.openflux.desktop.service.AppContainer
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.LocalScrollbars
import io.openflux.desktop.ui.Redact
import io.openflux.desktop.ui.components.AppIconButton
import io.openflux.desktop.ui.components.AppIcons
import io.openflux.desktop.ui.components.AppTextField
import io.openflux.desktop.ui.components.EmptyState
import io.openflux.desktop.ui.components.LocalToaster
import io.openflux.desktop.ui.components.PageHeader
import io.openflux.desktop.ui.components.Segmented
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

enum class LogFilter(val label: String) { All("Все"), Important("Важные"), Errors("Ошибки") }

class LogsScreenModel(private val container: AppContainer) : ScreenModel {
    val logs = container.connection.logs
    val settings = container.settings
    var query by mutableStateOf("")
    var filter by mutableStateOf(LogFilter.All)

    fun visible(lines: List<LogLine>): List<LogLine> {
        val q = query.trim().lowercase()
        return lines.filter { line ->
            val levelOk = when (filter) {
                LogFilter.All -> true
                LogFilter.Important -> line.level != LogLevel.Debug && line.level != LogLevel.Info || line.text.contains("OpenFlux")
                LogFilter.Errors -> line.level == LogLevel.Error || line.level == LogLevel.Warning
            }
            levelOk && (q.isEmpty() || line.text.lowercase().contains(q))
        }
    }

    fun copy(lines: List<LogLine>, mask: Boolean) =
        container.platform.setClipboardText(lines.joinToString("\n") { if (mask) Redact.apply(it.text) else it.text })

    fun clear() = container.connection.clearLogs()

    fun setAutoScroll(on: Boolean) = settings.update { it.copy(logAutoScroll = on) }
}

object LogsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Логи", icon = painterResource(AppIcons.Terminal))

    @Composable
    override fun Content() {
        val container = LocalAppContainer.current
        val model = rememberScreenModel { LogsScreenModel(container) }
        LogsScreen(model)
    }
}

@Composable
private fun LogsScreen(model: LogsScreenModel) {
    val all by model.logs.collectAsState()
    val settings by model.settings.settings.collectAsState()
    val toaster = LocalToaster.current
    val visible = model.visible(all)
    val listState = rememberLazyListState()
    val scrollbars = LocalScrollbars.current
    LaunchedEffect(visible.size, settings.logAutoScroll) {
        if (settings.logAutoScroll && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.xl)) {
        PageHeader("Журнал событий", "Хранится до закрытия приложения") {
            AppIconButton(
                if (settings.logAutoScroll) "Автопрокрутка включена" else "Автопрокрутка выключена",
                { model.setAutoScroll(!settings.logAutoScroll) },
                icon = Icons.Rounded.VerticalAlignBottom,
                selected = settings.logAutoScroll,
            )
            AppIconButton("Копировать показанное", {
                model.copy(visible, settings.maskSensitive)
                toaster.show("Скопировано строк: ${visible.size}")
            }, icon = Icons.Rounded.ContentCopy, enabled = visible.isNotEmpty())
            AppIconButton("Очистить журнал", model::clear, icon = Icons.Rounded.DeleteSweep, enabled = all.isNotEmpty())
        }
        Spacer(Modifier.height(AppTheme.spacing.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                value = model.query,
                onValueChange = { model.query = it },
                placeholder = "Поиск в журнале",
                modifier = Modifier.weight(1f),
                trailing = { Icon(Icons.Rounded.Search, null, tint = AppTheme.colors.textHint, modifier = Modifier.padding(end = AppTheme.spacing.s).size(18.dp)) },
            )
            Spacer(Modifier.width(AppTheme.spacing.m))
            Segmented(LogFilter.entries, model.filter, { it.label }, { model.filter = it }, Modifier.width(300.dp))
        }
        Spacer(Modifier.height(AppTheme.spacing.m))
        Box(
            Modifier
                .fillMaxSize()
                .clip(AppTheme.shapes.card)
                .background(AppTheme.colors.surface)
                .border(1.dp, AppTheme.colors.border, AppTheme.shapes.card),
        ) {
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        if (all.isEmpty()) "Журнал пуст" else "Нет строк под фильтр",
                        if (all.isEmpty()) "Здесь появятся сообщения ядра, когда вы подключитесь." else "Измените поиск или фильтр.",
                        resource = AppIcons.Terminal,
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.l, vertical = AppTheme.spacing.m)) {
                        items(visible, key = { it.id }) { line -> LogRow(line, settings.maskSensitive) }
                    }
                }
                scrollbars.Vertical(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun LogRow(line: LogLine, mask: Boolean) {
    val colors = AppTheme.colors
    val levelColor = when (line.level) {
        LogLevel.Error -> colors.danger
        LogLevel.Warning -> colors.warningPressed
        LogLevel.Success -> colors.success
        LogLevel.Debug -> colors.textHint
        LogLevel.Info -> colors.logText
    }
    val text = if (mask) Redact.apply(line.text) else line.text
    val tag = Regex("""\[[A-Z0-9-]+]""").find(text)
    Text(
        buildAnnotatedString {
            if (tag == null) {
                withStyle(SpanStyle(color = levelColor)) { append(text) }
            } else {
                withStyle(SpanStyle(color = levelColor)) { append(text.substring(0, tag.range.first)) }
                withStyle(SpanStyle(color = colors.accent)) { append(tag.value) }
                withStyle(SpanStyle(color = levelColor)) { append(text.substring(tag.range.last + 1)) }
            }
        },
        style = AppTheme.typography.mono,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    )
}

@Suppress("unused")
private val Transparent = Color.Transparent
