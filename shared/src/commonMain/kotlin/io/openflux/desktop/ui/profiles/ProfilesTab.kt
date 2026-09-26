package io.openflux.desktop.ui.profiles

import io.openflux.desktop.ui.PlatformBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import io.openflux.desktop.ui.components.ButtonRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.ValueKind
import io.openflux.desktop.model.isActive
import io.openflux.desktop.model.profile
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.LocalScrollbars
import io.openflux.desktop.ui.LocalShortcuts
import io.openflux.desktop.ui.LocalTouchUi
import io.openflux.desktop.ui.components.AppButton
import io.openflux.desktop.ui.components.AppCard
import io.openflux.desktop.ui.components.AppIconButton
import io.openflux.desktop.ui.components.AppIcons
import io.openflux.desktop.ui.components.AppMenu
import io.openflux.desktop.ui.components.AppTextField
import io.openflux.desktop.ui.components.ButtonStyle
import io.openflux.desktop.ui.components.ContextMenuArea
import io.openflux.desktop.ui.components.EmptyState
import io.openflux.desktop.ui.components.HorizontalRule
import io.openflux.desktop.ui.components.IconBubble
import io.openflux.desktop.ui.components.KeyValueRow
import io.openflux.desktop.ui.components.LocalToaster
import io.openflux.desktop.ui.components.MenuAction
import io.openflux.desktop.ui.components.PageHeader
import io.openflux.desktop.ui.components.SectionLabel
import io.openflux.desktop.ui.components.StatusBadge
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.look
import io.openflux.desktop.ui.node.NodeWizardPane
import io.openflux.desktop.ui.shell.LocalShell
import io.openflux.desktop.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

object ProfilesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Профили", icon = painterResource(AppIcons.Public))

    @Composable
    override fun Content() {
        val container = LocalAppContainer.current
        val model = rememberScreenModel { ProfilesScreenModel(container) }
        ProfilesScreen(model)
    }
}

@Composable
private fun ProfilesScreen(model: ProfilesScreenModel) {
    val profiles by model.profiles.profiles.collectAsState()
    val state by model.connection.state.collectAsState()
    val shell = LocalShell.current
    val shortcuts = LocalShortcuts.current
    val touch = LocalTouchUi.current
    val container = LocalAppContainer.current
    val incomingLink by container.incomingLink.collectAsState()

    // Requests from other screens (Home's empty state).
    LaunchedEffect(shell.importRequested, shell.newProfileRequested, shell.focusProfileId) {
        if (shell.importRequested) { model.importOpen = true; shell.importRequested = false }
        if (shell.newProfileRequested) { model.startNew(); shell.newProfileRequested = false }
        shell.focusProfileId?.let { id -> profiles.firstOrNull { it.id == id }?.let(model::select); shell.focusProfileId = null }
    }
    // A link opened from outside the app (a scanned code, a chat).
    LaunchedEffect(incomingLink) {
        val link = incomingLink ?: return@LaunchedEffect
        container.incomingLink.value = null
        model.importText = link
        model.importOpen = true
    }
    DisposableEffect(model) {
        val unregister = shortcuts.register { event ->
            if (event.type != KeyEventType.KeyDown || model.wizard != null) return@register false
            when {
                event.isCtrlPressed && event.key == Key.N -> { model.startNew(); true }
                event.isCtrlPressed && event.key == Key.I -> { model.importOpen = true; true }
                event.isCtrlPressed && event.key == Key.S && model.editor != null -> { model.save(); true }
                else -> false
            }
        }
        onDispose { unregister() }
    }

    val selected = profiles.firstOrNull { it.id == model.selectedId }
    val preferredId = LocalAppContainer.current.settings.settings.collectAsState().value.selectedProfileId
    val showDetail = model.editor != null || selected != null

    model.wizard?.let { wizard ->
        NodeWizardPane(wizard, onClose = { model.closeWizard() }, onSaved = { model.closeWizard(it) })
        return
    }

    // List and details side by side when both get a usable width, else one at a time.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxWidth < 720.dp
    PlatformBackHandler(enabled = touch && compact && showDetail) {
        if (model.editor != null) model.cancelEdit() else model.selectedId = null
    }
    val listWidth = (maxWidth * 0.32f).coerceIn(280.dp, 400.dp)
    // Side by side, an empty detail pane wastes the space: show the profile
    // Home would connect.
    LaunchedEffect(compact, selected == null, profiles.size) {
        if (!compact && selected == null && model.editor == null && profiles.isNotEmpty()) {
            model.selectedId = (profiles.firstOrNull { it.id == preferredId } ?: profiles.first()).id
        }
    }
    Row(Modifier.fillMaxSize()) {
        if (!compact || !showDetail) {
            ProfileListPane(
                model = model,
                profiles = profiles,
                state = state,
                modifier = if (compact) Modifier.fillMaxSize() else Modifier.width(listWidth).fillMaxHeight(),
            )
        }
        if (!compact || showDetail) {
            if (!compact) Box(Modifier.width(1.dp).fillMaxHeight().background(AppTheme.colors.border))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val editor = model.editor
                when {
                    editor != null -> ProfileEditor(model, editor, onBack = if (compact) ({ model.cancelEdit() }) else null)
                    selected != null -> ProfileDetails(model, selected, state, onBack = if (compact) ({ model.selectedId = null }) else null)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = if (profiles.isEmpty()) "Профилей пока нет" else "Выберите профиль",
                            message = if (profiles.isEmpty()) {
                                "Импортируйте ссылку openflux:// или QR-код от владельца ноды, создайте профиль вручную или поставьте свою ноду на VDS."
                            } else {
                                if (touch) "Подробности появятся здесь. Долгое нажатие на профиль открывает меню."
                                else "Подробности появятся здесь. Двойной щелчок по профилю подключает его, правый — открывает меню."
                            },
                            resource = AppIcons.Public,
                        ) {
                            ButtonRow(alignment = Alignment.CenterHorizontally) {
                                AppButton("Импорт", { model.importOpen = true }, leading = Icons.Rounded.ContentPaste)
                                AppButton("Создать", model::startNew, style = ButtonStyle.Secondary)
                                AppButton("Своя нода", model::openWizard, style = ButtonStyle.Secondary, leading = Icons.Rounded.Dns)
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (model.importOpen) ImportDialog(model)
    model.shareFor?.let { ShareDialog(model, it) }
    model.deleteFor?.let { DeleteDialog(model, it) }
}

@Composable
private fun ProfileListPane(model: ProfilesScreenModel, profiles: List<Profile>, state: ConnectionState, modifier: Modifier) {
    val filtered = model.filter(profiles)
    var addMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollbars = LocalScrollbars.current
    Column(modifier.padding(top = AppTheme.spacing.xl)) {
        PageHeader("Профили", "${profiles.size} ${plural(profiles.size)}", Modifier.padding(horizontal = AppTheme.spacing.xl)) {
            AppIconButton("Импорт ссылки или QR (Ctrl+I)", { model.importOpen = true }, resource = AppIcons.QrScan)
            Box {
                AppIconButton("Добавить профиль", { addMenu = true }, resource = AppIcons.Add, tint = AppTheme.colors.accent)
                AppMenu(addMenu, { addMenu = false }, listOf(
                    MenuAction("Создать вручную", model::startNew, Icons.Rounded.Edit, shortcut = "Ctrl+N"),
                    MenuAction("Импорт ссылки или QR", { model.importOpen = true }, Icons.Rounded.ContentPaste, shortcut = "Ctrl+I"),
                    MenuAction("Создать свою ноду на VDS", model::openWizard, Icons.Rounded.Dns),
                ))
            }
        }
        Spacer(Modifier.height(AppTheme.spacing.m))
        AppTextField(
            value = model.query,
            onValueChange = { model.query = it },
            placeholder = "Поиск по названию или адресу",
            modifier = Modifier.padding(horizontal = AppTheme.spacing.xl),
            trailing = {
                Icon(Icons.Rounded.Search, null, tint = AppTheme.colors.textHint, modifier = Modifier.padding(end = AppTheme.spacing.s).size(18.dp))
            },
        )
        Spacer(Modifier.height(AppTheme.spacing.m))
        if (filtered.isEmpty() && profiles.isNotEmpty()) {
            Text("Ничего не найдено", style = AppTheme.typography.body, color = AppTheme.colors.textSecondary, modifier = Modifier.padding(AppTheme.spacing.xl))
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered, key = { it.id }) { profile ->
                    val running = state.isActive && state.profile?.id == profile.id
                    ProfileRow(
                        profile = profile,
                        selected = profile.id == (model.editor?.draft?.id ?: model.selectedId),
                        runningLook = if (running) state.look() else null,
                        onClick = { model.select(profile) },
                        onDoubleClick = { model.connect(profile) },
                        menu = { profileActions(model, profile, running) },
                        onToggle = { if (running) model.connection.disconnect() else model.connect(profile) },
                        onEdit = { model.startEdit(profile) },
                        touch = LocalTouchUi.current,
                    )
                }
                item { Spacer(Modifier.height(AppTheme.spacing.l)) }
            }
            scrollbars.Vertical(listState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

private fun plural(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "профиль"
        mod10 in 2..4 && mod100 !in 12..14 -> "профиля"
        else -> "профилей"
    }
}

private fun profileActions(model: ProfilesScreenModel, profile: Profile, running: Boolean): List<MenuAction> = listOf(
    if (running) MenuAction("Отключить", model.connection::disconnect, Icons.Rounded.Stop)
    else MenuAction("Подключить", { model.connect(profile) }, Icons.Rounded.PlayArrow),
    MenuAction("Изменить", { model.startEdit(profile) }, Icons.Rounded.Edit),
    MenuAction("QR и ссылка", { model.shareFor = profile }, Icons.Rounded.QrCode2, enabled = profile.transport.shareable),
    MenuAction("Копировать ссылку", {
        model.shareLink(profile).onSuccess(model::copy)
    }, Icons.Rounded.ContentCopy, enabled = profile.transport.shareable),
    MenuAction("Дублировать", { model.duplicate(profile) }, Icons.Rounded.FileCopy),
    MenuAction("Удалить", { model.deleteFor = profile }, Icons.Rounded.Delete, danger = true),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    runningLook: io.openflux.desktop.ui.StateLook?,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    menu: () -> List<MenuAction>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    touch: Boolean,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> colors.accentSoft
            hovered -> colors.surfaceTonal.copy(alpha = if (colors.isDark) 1f else 0.6f)
            else -> Color.Transparent
        },
    )
    var moreOpen by remember { mutableStateOf(false) }
    ContextMenuArea(menu) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(AppTheme.dimens.listRow)
                .clip(AppTheme.shapes.card)
                .background(fill)
                .then(if (selected) Modifier.border(1.dp, colors.accent.copy(alpha = 0.45f), AppTheme.shapes.card) else Modifier)
                .hoverable(interaction)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onDoubleClick = if (touch) null else onDoubleClick,
                    onLongClick = if (touch) ({ moreOpen = true }) else null,
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = AppTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(AppIcons.byName(profile.icon))
            Spacer(Modifier.width(AppTheme.spacing.m))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = AppTheme.typography.bodyStrong, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(profile.summary, style = AppTheme.typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!touch && (hovered || moreOpen)) {
                AppIconButton(if (runningLook != null) "Отключить" else "Подключить", onToggle,
                    icon = if (runningLook != null) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, tint = colors.accent)
                AppIconButton("Изменить", onEdit, icon = Icons.Rounded.Edit)
                Box {
                    AppIconButton("Ещё", { moreOpen = true }, icon = Icons.Rounded.MoreVert)
                    AppMenu(moreOpen, { moreOpen = false }, menu())
                }
            } else {
                if (runningLook != null) StatusBadge(runningLook.title, runningLook.tone)
                // No hover on a touch screen: the menu stays in reach.
                if (touch) Box {
                    AppIconButton("Ещё", { moreOpen = true }, icon = Icons.Rounded.MoreVert)
                    AppMenu(moreOpen, { moreOpen = false }, menu())
                }
            }
        }
    }
}

@Composable
private fun ProfileDetails(model: ProfilesScreenModel, profile: Profile, state: ConnectionState, onBack: (() -> Unit)?) {
    val toaster = LocalToaster.current
    val running = state.isActive && state.profile?.id == profile.id
    val scroll = rememberScrollState()
    val scrollbars = LocalScrollbars.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.xl)) {
            Column(Modifier.widthIn(max = 960.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    AppIconButton("Назад к списку", onBack, resource = AppIcons.Back)
                    Spacer(Modifier.width(AppTheme.spacing.s))
                }
                IconBubble(AppIcons.byName(profile.icon), size = 48.dp)
                Spacer(Modifier.width(AppTheme.spacing.l))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = AppTheme.typography.pageTitle, color = AppTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(profile.summary, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (running) StatusBadge(state.look().title, state.look().tone)
            }
            Spacer(Modifier.height(AppTheme.spacing.xl))
            ButtonRow {
                if (running) AppButton("Отключить", model.connection::disconnect, style = ButtonStyle.Danger, leading = Icons.Rounded.Stop)
                else AppButton("Подключить", { model.connect(profile) }, leading = Icons.Rounded.PlayArrow)
                AppButton("Изменить", { model.startEdit(profile) }, style = ButtonStyle.Secondary, leading = Icons.Rounded.Edit)
                if (profile.transport.shareable) {
                    AppButton("QR и ссылка", { model.shareFor = profile }, style = ButtonStyle.Secondary, leading = Icons.Rounded.QrCode2)
                }
            }
            Spacer(Modifier.height(AppTheme.spacing.xxl))
            SectionLabel("Транспорты")
            Spacer(Modifier.height(AppTheme.spacing.s))
            AppCard(padding = 0.dp) {
                profile.carriers.forEachIndexed { index, carrier ->
                    val value = when (carrier.type.kind) {
                        ValueKind.Token -> "токен скрыт · UID ${carrier.uid}"
                        else -> carrier.value
                    }
                    KeyValueRow(
                        key = carrier.type.label + if (profile.session) " · приоритет ${carrier.priority}" else "",
                        value = value,
                    ) {
                        if (carrier.type.kind != ValueKind.Token) {
                            AppIconButton("Копировать", {
                                model.copy(carrier.value)
                                toaster.show("Скопировано")
                            }, icon = Icons.Rounded.ContentCopy)
                        }
                    }
                    if (index < profile.carriers.lastIndex) HorizontalRule()
                }
            }
            Spacer(Modifier.height(AppTheme.spacing.xl))
            SectionLabel("Параметры")
            Spacer(Modifier.height(AppTheme.spacing.s))
            AppCard(padding = 0.dp) {
                KeyValueRow("Режим", if (profile.session) "Session (несколько транспортов)" else "Обычный")
                HorizontalRule()
                KeyValueRow("Шифрование", if (profile.secret.isNotEmpty()) "AES-256-GCM, ключ ${profile.secret.length} симв." else "Без ключа")
                if (!profile.session) {
                    HorizontalRule()
                    KeyValueRow("Кодек", profile.codec.label)
                }
                HorizontalRule()
                KeyValueRow("Источник", profile.source.label)
            }
            val problems = profile.problems()
            if (problems.isNotEmpty()) {
                Spacer(Modifier.height(AppTheme.spacing.l))
                io.openflux.desktop.ui.components.Banner(problems.joinToString("\n"), Tone.Warning)
            }
            Spacer(Modifier.height(AppTheme.spacing.xl))
            ButtonRow {
                AppButton("Дублировать", { model.duplicate(profile) }, style = ButtonStyle.Ghost, leading = Icons.Rounded.FileCopy)
                AppButton("Удалить", { model.deleteFor = profile }, style = ButtonStyle.Ghost, leading = Icons.Rounded.Delete)
            }
            }
        }
        scrollbars.Vertical(scroll, Modifier.align(Alignment.CenterEnd))
    }
}
