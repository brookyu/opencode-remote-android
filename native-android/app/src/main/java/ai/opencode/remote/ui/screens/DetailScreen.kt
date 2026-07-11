package ai.opencode.remote.ui.screens

import ai.opencode.remote.R
import ai.opencode.remote.data.models.*
import ai.opencode.remote.extractText
import ai.opencode.remote.formatLimit
import ai.opencode.remote.formatTime
import ai.opencode.remote.normalizeMessageMarkdown
import ai.opencode.remote.viewmodel.DetailUiState
import ai.opencode.remote.viewmodel.SessionsUiState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onComposerChange: (String) -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onModelKeyChange: (String) -> Unit,
    onAgentChange: (String) -> Unit,
    onModelQueryChange: (String) -> Unit,
    onToggleTodos: () -> Unit,
    onShowAiSheet: () -> Unit,
    onHideAiSheet: () -> Unit,
    onShowDetailsSheet: () -> Unit,
    onHideDetailsSheet: () -> Unit,
    onErrorDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.renderedMessages.size, state.showTypingBubble) {
        if (state.renderedMessages.isNotEmpty()) {
            val target = state.renderedMessages.size - 1
            if (state.showTypingBubble) target + 1 else target
            listState.animateScrollToItem(target)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.sessionTitle.ifBlank { "Session" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state.sessionDirectory.isNotBlank()) {
                            Text(
                                state.sessionDirectory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    if (state.isWorking) {
                        IconButton(onClick = onAbort) {
                            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.detail_abort), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        bottomBar = {
            ComposerBar(
                text = state.composerText,
                onChange = onComposerChange,
                onSend = onSend,
                isWorking = state.isWorking,
                modifier = Modifier.imePadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val stripScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
                    .horizontalScroll(stripScroll)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onShowAiSheet,
                    label = {
                        Text(
                            state.activeAgent?.name ?: state.selectedAgentId,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Text(
                            stringResource(R.string.detail_ai_chip),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                AssistChip(
                    onClick = onShowAiSheet,
                    label = {
                        Text(
                            state.activeModelOption?.let { "${it.providerName} / ${it.modelName}" }
                                ?: stringResource(R.string.detail_model_loading),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )

                AssistChip(
                    onClick = onShowDetailsSheet,
                    label = {
                        Text(
                            state.project?.name ?: stringResource(R.string.details_no_project),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Text(
                            stringResource(R.string.detail_details_chip),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                StatusBadge(state.sessionStatus)
            }

            state.error?.let { err ->
                Snackbar(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .padding(4.dp),
                    action = { TextButton(onClick = onErrorDismiss) { Text(stringResource(R.string.close)) } }
                ) { Text(err) }
            }

            if (state.todos.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                ) {
                    TodoSection(
                        todos = state.todos,
                        expanded = state.todosExpanded,
                        onToggle = onToggleTodos
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
            ) {
                if (state.renderedMessages.isEmpty() && !state.isLoading && !state.showTypingBubble) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.detail_no_messages),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.renderedMessages) { (msg, text) ->
                            MessageBubble(
                                role = msg.info.role,
                                text = text
                            )
                        }
                        if (state.showTypingBubble) {
                            item { TypingIndicator() }
                        }
                    }
                }
            }
        }
    }

    if (state.showAiSheet) {
        ModalBottomSheet(onDismissRequest = onHideAiSheet) {
            AiSheetContent(
                state = state,
                onModelKeyChange = onModelKeyChange,
                onAgentChange = onAgentChange,
                onModelQueryChange = onModelQueryChange
            )
        }
    }

    if (state.showDetailsSheet) {
        ModalBottomSheet(onDismissRequest = onHideDetailsSheet) {
            DetailsSheetContent(state = state)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "busy" -> MaterialTheme.colorScheme.tertiary
        "retry" -> MaterialTheme.colorScheme.error
        "waiting" -> MaterialTheme.colorScheme.secondary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val pulseTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val bgAlpha = if (status == "busy") 0.10f + 0.18f * pulse else 0.15f
    Surface(
        color = color.copy(alpha = bgAlpha),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun TodoSection(
    todos: List<TodoItem>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val pending = todos.count { it.status == "pending" }
    val inProgress = todos.count { it.status == "in_progress" }
    val completed = todos.count { it.status == "completed" }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.detail_todo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$completed/${todos.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (inProgress > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text("· $inProgress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                todos.forEach { todo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        val iconColor = when (todo.status) {
                            "completed" -> MaterialTheme.colorScheme.tertiary
                            "in_progress" -> MaterialTheme.colorScheme.primary
                            "cancelled" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline
                        }
                        val icon = when (todo.status) {
                            "completed" -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.PlayArrow
                        }
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            todo.content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(role: String, text: String) {
    val isUser = role == "user"
    val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fgColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val label = if (isUser) "You" else "OpenCode"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp)
        )
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 640.dp)
        ) {
            if (isUser) {
                Text(
                    normalizeMessageMarkdown(text),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = fgColor
                )
            } else {
                Markdown(
                    content = text,
                    colors = markdownColor(
                        text = MaterialTheme.colorScheme.onSurfaceVariant,
                        codeText = MaterialTheme.colorScheme.primary,
                        inlineCodeText = MaterialTheme.colorScheme.tertiary,
                        linkText = MaterialTheme.colorScheme.primary,
                        codeBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        inlineCodeBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        dividerColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    typography = markdownTypography(
                        text = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        h1 = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        h2 = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        h3 = MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        ),
                        h4 = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        ),
                        h5 = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        ),
                        h6 = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        ),
                        code = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        inlineCode = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.tertiary
                        ),
                        quote = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontStyle = FontStyle.Italic
                        ),
                        link = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dots = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0.3f at 0 with LinearEasing
                    1f at (index * 200) with LinearEasing
                    1f at (index * 200 + 250) with LinearEasing
                    0.3f at (index * 200 + 500) with LinearEasing
                    0.3f at 1200 with LinearEasing
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_$index"
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dots.forEach { anim ->
                    val v = anim.value
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(0.6f + 0.4f * v)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = v), CircleShape)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerBar(
    text: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    isWorking: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = text,
                        onValueChange = onChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                                    if (keyEvent.isShiftPressed) {
                                        onChange(text + "\n")
                                    } else {
                                        if (text.isNotBlank() && !isWorking) {
                                            onSend()
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                        placeholder = { Text(stringResource(R.string.detail_composer_hint)) },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions.Default
                    )
                    Spacer(Modifier.width(4.dp))
                    FloatingActionButton(
                        onClick = onSend,
                        shape = CircleShape,
                        containerColor = if (isWorking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (isWorking) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            if (isWorking) Icons.Filled.Stop else Icons.Filled.Send,
                            contentDescription = if (isWorking) stringResource(R.string.detail_stop) else stringResource(R.string.detail_send)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSheetContent(
    state: DetailUiState,
    onModelKeyChange: (String) -> Unit,
    onAgentChange: (String) -> Unit,
    onModelQueryChange: (String) -> Unit
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.ai_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            stringResource(R.string.ai_sheet_agent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (state.primaryAgents.isEmpty()) {
            Text(
                stringResource(R.string.ai_sheet_no_agents),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.primaryAgents.forEach { agent ->
                    FilterChip(
                        selected = state.selectedAgentId == agent.id,
                        onClick = { onAgentChange(agent.id) },
                        label = { Text(agent.name) }
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            stringResource(R.string.ai_sheet_model),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = state.modelQuery,
            onValueChange = onModelQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            placeholder = { Text(stringResource(R.string.ai_sheet_search_models)) },
            singleLine = true
        )

        if (state.filteredModels.isEmpty()) {
            Text(
                stringResource(R.string.ai_sheet_no_models),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.filteredModels, key = { "${it.providerID}/${it.modelID}/${it.variant ?: ""}" }) { model ->
                    val sel = state.selectedModel
                    val isSelected = sel != null &&
                        sel.providerID == model.providerID &&
                        sel.modelID == model.modelID &&
                        (sel.variant ?: "") == (model.variant ?: "")
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onModelKeyChange(
                                    listOf(
                                        java.net.URLEncoder.encode(model.providerID, "UTF-8"),
                                        java.net.URLEncoder.encode(model.modelID, "UTF-8"),
                                        java.net.URLEncoder.encode(model.variant ?: "", "UTF-8")
                                    ).joinToString("|")
                                )
                            },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${model.providerName} / ${model.modelName}" +
                                            (model.variant?.let { " ($it)" } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    val caps = buildList {
                                        if (model.tools == true) add(stringResource(R.string.ai_sheet_tools))
                                        if (model.attachments == true) add(stringResource(R.string.ai_sheet_attachments))
                                        model.contextLimit?.let { add("${stringResource(R.string.ai_sheet_context)}: ${formatLimit(it)}") }
                                        model.outputLimit?.let { add("${stringResource(R.string.ai_sheet_output)}: ${formatLimit(it)}") }
                                    }
                                    if (caps.isNotEmpty()) {
                                        Text(
                                            caps.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (model.isDefault == true) {
                                    AssistChip(onClick = {}, label = { Text("default") })
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DetailsSheetContent(state: DetailUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.details_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            stringResource(R.string.details_project),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val project = state.project
        if (project != null && (project.name != null || project.path != null)) {
            Text(
                "${project.name ?: "—"}\n${project.path ?: project.directory ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            Text(
                stringResource(R.string.details_no_project),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            stringResource(R.string.details_vcs),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        val vcs = state.vcs
        if (vcs != null && vcs.branch != null) {
            Text(
                "${stringResource(R.string.details_branch)}: ${vcs.branch}" +
                    (vcs.status?.let { " ($it)" } ?: "") +
                    (vcs.ahead?.let { " ↑$it" } ?: "") +
                    (vcs.behind?.let { " ↓$it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            Text(
                stringResource(R.string.details_no_vcs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            stringResource(R.string.details_files),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (state.fileStatuses.isNotEmpty()) {
            state.fileStatuses.forEach { fs ->
                Text(
                    "${fs.status ?: "?"} ${fs.file ?: fs.path ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        } else {
            Text(
                stringResource(R.string.details_no_files),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            stringResource(R.string.details_diff),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (state.diffFiles.isNotEmpty()) {
            Text(
                "+${state.totalDiffAdditions} -${state.totalDiffDeletions}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            state.diffFiles.forEach { df ->
                Text(
                    "${df.file}: +${df.additions} -${df.deletions}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        } else {
            Text(
                stringResource(R.string.details_no_diff),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}