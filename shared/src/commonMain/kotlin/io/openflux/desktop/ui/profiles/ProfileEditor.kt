package io.openflux.desktop.ui.profiles

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextOverflow
import io.openflux.desktop.ui.components.fillUpTo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.openflux.desktop.model.Codec
import io.openflux.desktop.model.ExtraTransport
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.TransportType
import io.openflux.desktop.model.ValueKind
import io.openflux.desktop.ui.LocalScrollbars
import io.openflux.desktop.ui.LocalShortcuts
import io.openflux.desktop.ui.components.AppButton
import io.openflux.desktop.ui.components.AppCard
import io.openflux.desktop.ui.components.AppIconButton
import io.openflux.desktop.ui.components.AppIcons
import io.openflux.desktop.ui.components.AppMenu
import io.openflux.desktop.ui.components.AppTextField
import io.openflux.desktop.ui.components.Banner
import io.openflux.desktop.ui.components.ButtonStyle
import io.openflux.desktop.ui.components.LocalToaster
import io.openflux.desktop.ui.components.MenuAction
import io.openflux.desktop.ui.components.SectionLabel
import io.openflux.desktop.ui.components.Segmented
import io.openflux.desktop.ui.components.TextAction
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.components.appClickable
import io.openflux.desktop.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

/** Creates or changes a profile; Ctrl+S saves, Esc cancels. */
@Composable
fun ProfileEditor(model: ProfilesScreenModel, state: EditorState, onBack: (() -> Unit)?) {
    val draft = state.draft
    val toaster = LocalToaster.current
    val shortcuts = LocalShortcuts.current
    val scroll = rememberScrollState()
    val scrollbars = LocalScrollbars.current
    DisposableEffect(model) {
        val unregister = shortcuts.register { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) { model.cancelEdit(); true } else false
        }
        onDispose { unregister() }
    }
    val save = {
        if (model.save()) toaster.show(if (state.isNew) "Профиль добавлен" else "Профиль сохранён", Tone.Success)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    AppIconButton("Назад", onBack, resource = AppIcons.Back)
                    Spacer(Modifier.width(AppTheme.spacing.s))
                }
                Text(
                    if (state.isNew) "Новый профиль" else "Изменить профиль",
                    style = AppTheme.typography.pageTitle,
                    color = AppTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppButton("Отмена", model::cancelEdit, style = ButtonStyle.Secondary)
                Spacer(Modifier.width(AppTheme.spacing.s))
                AppButton("Сохранить", save)
            }
            Text("Ctrl+S — сохранить, Esc — отменить", style = AppTheme.typography.caption, color = AppTheme.colors.textHint)
            Spacer(Modifier.height(AppTheme.spacing.xl))

            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xl)) {
                if (state.showProblems) {
                    val problems = draft.problems()
                    if (problems.isNotEmpty()) Banner(problems.joinToString("\n"), Tone.Danger, icon = Icons.Rounded.WarningAmber)
                }

                AppTextField(draft.name, { v -> model.updateDraft { it.copy(name = v) } }, label = "Название", placeholder = "Например, Нода в Нидерландах")

                Column {
                    SectionLabel("Значок")
                    Spacer(Modifier.height(AppTheme.spacing.s))
                    IconPicker(draft.icon) { icon -> model.updateDraft { it.copy(icon = icon) } }
                }

                Column {
                    SectionLabel("Режим")
                    Spacer(Modifier.height(AppTheme.spacing.s))
                    Segmented(
                        options = listOf(false, true),
                        selected = draft.session,
                        label = { if (it) "Session" else "Обычный" },
                        onSelect = { session ->
                            model.updateDraft {
                                val transport = if (!session && it.transport.sessionOnly) TransportType.VYANDEX else it.transport
                                it.copy(session = session, transport = transport)
                            }
                        },
                        modifier = Modifier.fillUpTo(320.dp),
                    )
                    Spacer(Modifier.height(AppTheme.spacing.xs))
                    Text(
                        if (draft.session) {
                            "Несколько транспортов одновременно с переключением по приоритету, согласованный ключ. Нода должна работать в режиме Session."
                        } else {
                            "Один транспорт, как у нод со старой настройкой."
                        },
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                    )
                }

                AppCard {
                    SectionLabel(if (draft.session) "Основной транспорт" else "Транспорт")
                    Spacer(Modifier.height(AppTheme.spacing.m))
                    CarrierFields(
                        carrier = ExtraTransport(draft.transport, draft.value, draft.uid, draft.priority),
                        session = draft.session,
                        showPriority = draft.session,
                        onChange = { c -> model.updateDraft { it.copy(transport = c.type, value = c.value, uid = c.uid, priority = c.priority) } },
                    )
                    if (!draft.session) {
                        Spacer(Modifier.height(AppTheme.spacing.l))
                        Text("Кодек", style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        Segmented(Codec.entries, draft.codec, { it.label }, { c -> model.updateDraft { it.copy(codec = c) } }, Modifier.fillUpTo(360.dp))
                    }
                }

                if (draft.session) {
                    Column {
                        SectionLabel("Дополнительные транспорты")
                        Spacer(Modifier.height(AppTheme.spacing.s))
                        draft.extras.forEachIndexed { index, extra ->
                            AppCard(Modifier.padding(bottom = AppTheme.spacing.s)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Транспорт ${index + 2}", style = AppTheme.typography.bodyStrong, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
                                    AppIconButton("Убрать транспорт", {
                                        model.updateDraft { p -> p.copy(extras = p.extras.filterIndexed { i, _ -> i != index }) }
                                    }, icon = Icons.Rounded.Close)
                                }
                                Spacer(Modifier.height(AppTheme.spacing.s))
                                CarrierFields(extra, session = true, showPriority = true) { changed ->
                                    model.updateDraft { p -> p.copy(extras = p.extras.mapIndexed { i, e -> if (i == index) changed else e }) }
                                }
                            }
                        }
                        AppButton("Добавить транспорт", {
                            model.updateDraft { it.copy(extras = it.extras + ExtraTransport(TransportType.DIRECT, priority = 50)) }
                        }, style = ButtonStyle.Secondary, leading = Icons.Rounded.Add)
                    }
                }

                Column {
                    AppTextField(
                        value = draft.secret,
                        onValueChange = { v -> model.updateDraft { it.copy(secret = v.trim()) } },
                        label = "Ключ шифрования",
                        placeholder = if (draft.session) "Обязателен в режиме Session" else "Необязательно",
                        secret = true,
                        monospace = true,
                        helper = "Тот же ключ, что у ноды. Не короче ${Profile.MIN_SECRET} символов.",
                    )
                    TextAction("Сгенерировать безопасный ключ", { model.updateDraft { it.copy(secret = model.newSecret()) } })
                }

                if (draft.session) {
                    AppTextField(
                        value = draft.context,
                        onValueChange = { v -> model.updateDraft { it.copy(context = v.trim()) } },
                        label = "Контекст шифрования (необязательно)",
                        placeholder = "Адрес основного документа",
                        helper = "Приходит со ссылкой openflux://. Если пусто, берётся адрес документа с наибольшим приоритетом, как у ноды.",
                    )
                }
            }
            Spacer(Modifier.height(AppTheme.spacing.xxxl))
        }
        scrollbars.Vertical(scroll, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun CarrierFields(carrier: ExtraTransport, session: Boolean, showPriority: Boolean, onChange: (ExtraTransport) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)) {
        TransportDropdown(carrier.type, session) { onChange(carrier.copy(type = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)) {
            AppTextField(
                value = carrier.value,
                onValueChange = { onChange(carrier.copy(value = it.trim())) },
                label = when (carrier.type.kind) {
                    ValueKind.DocumentUrl -> if (carrier.type == TransportType.CUPSONLINE) "Код комнат" else "Ссылка на документ"
                    ValueKind.Address -> "Адрес ноды"
                    ValueKind.Token -> "Токен MAX Web"
                },
                placeholder = carrier.type.valueHint,
                secret = carrier.type.kind == ValueKind.Token,
                error = if (carrier.value.isNotBlank()) Profile.carrierProblem(carrier)?.takeIf { carrier.type.kind != ValueKind.Token } else null,
                modifier = Modifier.weight(1f),
            )
            if (showPriority) {
                AppTextField(
                    value = carrier.priority.toString(),
                    onValueChange = { v -> v.filter(Char::isDigit).take(3).toIntOrNull()?.let { onChange(carrier.copy(priority = it)) } },
                    label = "Приоритет",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.width(110.dp),
                )
            }
        }
        if (carrier.type == TransportType.ONEME) {
            AppTextField(carrier.uid, { onChange(carrier.copy(uid = it.trim())) }, label = "ID пользователя MAX", placeholder = "Число из адреса звонка")
        }
    }
}

@Composable
private fun TransportDropdown(selected: TransportType, session: Boolean, onSelect: (TransportType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box {
        Row(
            Modifier
                .fillUpTo(480.dp)
                .height(AppTheme.dimens.fieldHeight)
                .clip(AppTheme.shapes.field)
                .background(AppTheme.colors.surface)
                .border(1.dp, if (hovered) AppTheme.colors.textHint else AppTheme.colors.border, AppTheme.shapes.field)
                .appClickable(interaction) { open = true }
                .padding(horizontal = AppTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(AppIcons.byName(selected.icon)), null, tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppTheme.spacing.s))
            Text(selected.label, style = AppTheme.typography.body, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, "Выбрать транспорт", tint = AppTheme.colors.textSecondary)
        }
        AppMenu(open, { open = false }, TransportType.entries.filter { session || !it.sessionOnly }.map { type ->
            MenuAction(type.label, { onSelect(type) })
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
        Profile.ICONS.forEach { name ->
            val isSelected = name == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val fill by animateColorAsState(
                when {
                    isSelected -> AppTheme.colors.accent
                    hovered -> AppTheme.colors.surfaceTonal
                    else -> AppTheme.colors.surface
                },
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(AppTheme.shapes.field)
                    .background(fill)
                    .border(1.dp, if (isSelected) Color.Transparent else AppTheme.colors.border, AppTheme.shapes.field)
                    .appClickable(interaction) { onSelect(name) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(AppIcons.byName(name)), name, tint = if (isSelected) Color.White else AppTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
