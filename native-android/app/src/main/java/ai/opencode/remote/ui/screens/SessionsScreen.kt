package ai.opencode.remote.ui.screens

import ai.opencode.remote.viewmodel.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import ai.opencode.remote.R
import ai.opencode.remote.formatTime
import ai.opencode.remote.parentDirectory
import ai.opencode.remote.data.models.FileEntry
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    state: SessionsUiState,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onNewSession: () -> Unit,
    onSessionClick: (SessionView) -> Unit,
    onDeleteSession: (SessionView) -> Unit,
    onToggleProjectCollapsed: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onPickerBrowse: (String) -> Unit,
    onPickerSelectDir: (String) -> Unit,
    onPickerCreate: (String?) -> Unit,
    onPickerDismiss: () -> Unit,
    onCommandFilterChange: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val swipeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier
            .offset { IntOffset(swipeOffset.value.toInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        scope.launch {
                            if (swipeOffset.value < -screenWidthPx * 0.25f) {
                                swipeOffset.animateTo(-screenWidthPx, tween(200))
                                onSwipeLeft()
                                swipeOffset.snapTo(0f)
                            } else {
                                swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            val nextOffset = minOf(0f, swipeOffset.value + dragAmount)
                            swipeOffset.snapTo(nextOffset)
                        }
                    }
                )
            },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSession,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.sessions_new)) }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sessions_refresh))
                        }
                    }
                    IconButton(onClick = onHelpClick) {
                        Icon(Icons.Filled.Help, contentDescription = stringResource(R.string.help_title))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = state.connectionState != ConnectionState.CONNECTED &&
                    state.connectionState != ConnectionState.IDLE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ConnectionBanner(state.connectionState)
            }

            state.error?.let { err ->
                Snackbar(
                    modifier = Modifier.padding(8.dp),
                    action = { TextButton(onClick = onErrorDismiss) { Text(stringResource(R.string.close)) } }
                ) { Text(err) }
            }

            if (state.sessions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.sessions_total).replace("{total}", state.sessions.size.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.activeCount > 0) {
                        Text(
                            stringResource(R.string.sessions_active).replace("{active}", state.activeCount.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (state.changedCount > 0) {
                        Text(
                            stringResource(R.string.sessions_changed).replace("{changed}", state.changedCount.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.sessions_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            val groupedSessions = remember(state.filteredSessions) {
                state.filteredSessions.groupBy { it.directory }
            }

            if (groupedSessions.isEmpty() && !state.isRefreshing) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedSessions.forEach { (directory, sessionsInProj) ->
                        val projectName = directory.substringAfterLast('/').ifBlank { "Global" }
                        val isCollapsed = state.collapsedProjects.contains(directory)
                        
                        item(key = "dir-$directory") {
                            FolderHeader(
                                name = projectName,
                                path = directory,
                                isCollapsed = isCollapsed,
                                sessionCount = sessionsInProj.size,
                                onClick = { onToggleProjectCollapsed(directory) }
                            )
                        }
                        
                        if (!isCollapsed) {
                            items(sessionsInProj, key = { it.id }) { session ->
                                Box(
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    SessionCard(
                                        session = session,
                                        onClick = { onSessionClick(session) },
                                        onDelete = { onDeleteSession(session) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.sessions_delete_title)) },
            text = { Text(stringResource(R.string.sessions_delete_message).replace("{title}", session.title)) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.sessions_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.sessions_delete_cancel)) }
            }
        )
    }

    if (state.showNewSessionPicker) {
        FolderPickerDialog(
            state = state,
            onBrowse = onPickerBrowse,
            onSelectDir = onPickerSelectDir,
            onCreate = onPickerCreate,
            onDismiss = onPickerDismiss
        )
    }
}

@Composable
private fun ConnectionBanner(state: ConnectionState) {
    val (bg, fg, text) = when (state) {
        ConnectionState.CONNECTING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            stringResource(R.string.sessions_connection_connecting)
        )
        ConnectionState.RECONNECTING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            stringResource(R.string.sessions_connection_reconnecting)
        )
        ConnectionState.OFFLINE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            stringResource(R.string.sessions_connection_offline)
        )
        ConnectionState.IDLE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            ""
        )
        ConnectionState.CONNECTED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            ""
        )
    }
    if (text.isNotEmpty()) {
        Surface(
            color = bg,
            contentColor = fg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(fg)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                stringResource(R.string.sessions_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.sessions_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionView,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = sessionStatusColor(session.status)
    val cardShape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        session.title.ifBlank { "Untitled" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusPill(status = session.status)
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.sessions_delete_confirm),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (session.directory.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        session.directory,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                session.model?.let { model ->
                    Spacer(Modifier.height(6.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = model.modelID,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = null,
                        modifier = Modifier.height(24.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (session.files > 0 || session.additions > 0 || session.deletions > 0) {
                        Text(
                            "+${session.additions} -${session.deletions} (${session.files} files)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        formatTime(session.updated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun sessionStatusColor(status: String): androidx.compose.ui.graphics.Color {
    return when (status) {
        "busy" -> MaterialTheme.colorScheme.tertiary
        "retry" -> MaterialTheme.colorScheme.error
        "error" -> MaterialTheme.colorScheme.error
        "finished" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
}

@Composable
private fun StatusPill(status: String) {
    val (color, labelRes) = when (status) {
        "busy" -> MaterialTheme.colorScheme.tertiary to R.string.sessions_status_busy
        "retry" -> MaterialTheme.colorScheme.error to R.string.sessions_status_retry
        "waiting" -> MaterialTheme.colorScheme.secondary to R.string.sessions_status_waiting
        "error" -> MaterialTheme.colorScheme.error to R.string.sessions_status_error
        "aborted" -> MaterialTheme.colorScheme.outline to R.string.sessions_status_aborted
        "finished" -> MaterialTheme.colorScheme.tertiary to R.string.sessions_status_finished
        else -> MaterialTheme.colorScheme.outline to R.string.sessions_status_idle
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FolderPickerDialog(
    state: SessionsUiState,
    onBrowse: (String) -> Unit,
    onSelectDir: (String) -> Unit,
    onCreate: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.picker_current, state.pickerPath.ifBlank { "/" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    state.pickerError?.let { err ->
                        Text(
                            stringResource(R.string.picker_error, err),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (state.pickerLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        ) {
                            val parent = parentDirectory(state.pickerPath)
                            if (parent != null) {
                                item {
                                    FolderRow(
                                        name = stringResource(R.string.picker_parent),
                                        onClick = { onBrowse(parent) }
                                    )
                                }
                            }

                            if (state.pickerItems.isEmpty()) {
                                item {
                                    Text(
                                        stringResource(R.string.picker_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            items(state.pickerItems, key = { it.absolute }) { item ->
                                FolderRow(
                                    name = item.name,
                                    onClick = { onBrowse(item.absolute) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(state.pickerPath.ifBlank { null }) },
                enabled = !state.isCreating
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.picker_select))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
        }
    )
}

@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FolderHeader(
    name: String,
    path: String,
    isCollapsed: Boolean,
    sessionCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (path.isNotBlank()) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            SuggestionChip(
                onClick = onClick,
                label = { Text(sessionCount.toString()) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}