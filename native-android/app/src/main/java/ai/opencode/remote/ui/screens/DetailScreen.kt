package ai.opencode.remote.ui.screens

import ai.opencode.remote.R
import ai.opencode.remote.data.models.*
import ai.opencode.remote.extractText
import ai.opencode.remote.formatLimit
import ai.opencode.remote.formatTime
import ai.opencode.remote.normalizeMessageMarkdown
import ai.opencode.remote.viewmodel.DetailUiState
import ai.opencode.remote.viewmodel.SessionsUiState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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

    // Auto-scroll to bottom when new messages arrive
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
                isWorking = state.isWorking
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Context strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // AI chip
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

                // Details chip
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

                // Status pill
                Spacer(Modifier.weight(1f))
                StatusBadge(state.sessionStatus)
            }

            // Error banner
            state.error?.let { err ->
                Snackbar(
                    modifier = Modifier.padding(4.dp),
                    action = { TextButton(onClick = onErrorDismiss) { Text(stringResource(R.string.close)) } }
                ) { Text(err) }
            }

            // Todos (collapsible)
            if (state.todos.isNotEmpty()) {
                TodoSection(
                    todos = state.todos,
                    expanded = state.todosExpanded,
                    onToggle = onToggleTodos
                )
            }

            // Messages list
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

    // AI Sheet
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

    // Details Sheet
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
    Surface(
        color = color.copy(alpha = 0.15f),
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
        shape = RoundedCornerShape(8.dp)
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                normalizeMessageMarkdown(text),
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = fgColor
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                stringResource(R.string.detail_typing),
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComposerBar(
    text: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    isWorking: Boolean
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.detail_composer_hint)) },
                maxLines = 5,
                keyboardOptions = KeyboardOptions.Default
            )
            Spacer(Modifier.width(8.dp))
            if (isWorking) {
                FloatingActionButton(
                    onClick = onSend,
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.detail_stop))
                }
            } else {
                FloatingActionButton(
                    onClick = onSend,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.detail_send))
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

        // Agent section
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

        // Model section
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
                            .clip(RoundedCornerShape(8.dp))
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
                        Column(modifier = Modifier.padding(10.dp)) {
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

        // Project info
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

        // VCS info
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

        // File statuses
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

        // Diff
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
