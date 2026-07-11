package ai.opencode.remote.ui.screens

import ai.opencode.remote.R
import ai.opencode.remote.data.models.CommandInfo
import ai.opencode.remote.viewmodel.SessionsUiState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    state: SessionsUiState,
    onCommandFilterChange: (String) -> Unit,
    onCommandSearchChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.help_tab_overview),
        stringResource(R.string.help_tab_server),
        stringResource(R.string.help_tab_network),
        stringResource(R.string.help_tab_troubleshooting),
        stringResource(R.string.help_tab_commands)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back)
                        )
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
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            when (selectedTab) {
                0 -> InfoContent(stringResource(R.string.help_overview_text))
                1 -> InfoContent(stringResource(R.string.help_server_text))
                2 -> InfoContent(stringResource(R.string.help_network_text))
                3 -> InfoContent(stringResource(R.string.help_troubleshooting_text))
                4 -> CommandsTab(state, onCommandFilterChange, onCommandSearchChange)
            }
        }
    }
}

@Composable
private fun InfoContent(text: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CommandsTab(
    state: SessionsUiState,
    onCommandFilterChange: (String) -> Unit,
    onCommandSearchChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        OutlinedTextField(
            value = state.commandSearch,
            onValueChange = onCommandSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.help_filter_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            trailingIcon = {
                if (state.commandSearch.isNotEmpty()) {
                    IconButton(onClick = { onCommandSearchChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                    }
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.commandFilter == "all",
                onClick = { onCommandFilterChange("all") },
                label = { Text(stringResource(R.string.help_filter_all)) }
            )
            FilterChip(
                selected = state.commandFilter == "skill",
                onClick = { onCommandFilterChange("skill") },
                label = { Text(stringResource(R.string.help_filter_skills)) }
            )
        }

        val skillCommands = state.filteredCommands.filter { it.source == "skill" }
        val otherCommands = state.filteredCommands.filterNot { it.source == "skill" }

        if (state.filteredCommands.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.help_no_commands),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (otherCommands.isNotEmpty()) {
                    item(key = "header-commands") {
                        SectionHeader(stringResource(R.string.help_tab_commands))
                    }
                    items(otherCommands, key = { "cmd-${it.name}" }) { cmd ->
                        CommandCard(cmd)
                    }
                }
                if (skillCommands.isNotEmpty()) {
                    item(key = "header-skills") {
                        SectionHeader(stringResource(R.string.help_filter_skills))
                    }
                    items(skillCommands, key = { "skill-${it.name}" }) { cmd ->
                        CommandCard(cmd)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun CommandCard(cmd: CommandInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "/${cmd.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (cmd.source == "skill") {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.help_filter_skills)) })
                }
            }
            cmd.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}