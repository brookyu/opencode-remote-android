package ai.opencode.remote.ui.screens

import ai.opencode.remote.Language
import ai.opencode.remote.ThemePref
import ai.opencode.remote.viewmodel.NoticeType
import ai.opencode.remote.viewmodel.SettingsNotice
import ai.opencode.remote.viewmodel.SettingsUiState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ai.opencode.remote.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLanguageChange: (Language) -> Unit,
    onThemeChange: (ThemePref) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onNoticeDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var portText by remember(state.draftConfig.port) {
        mutableStateOf(if (state.draftConfig.port > 0) state.draftConfig.port.toString() else "")
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server connection section
            Text(
                text = stringResource(R.string.settings_host),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = state.draftConfig.host,
                onValueChange = onHostChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_host_hint)) },
                singleLine = true
            )

            Text(
                text = stringResource(R.string.settings_port),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { s ->
                    val n = s.filter { it.isDigit() }.toIntOrNull()
                    if (n != null) {
                        portText = s
                        onPortChange(n)
                    } else if (s.isEmpty()) {
                        portText = ""
                        onPortChange(0)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_port_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text(
                text = stringResource(R.string.settings_username),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = state.draftConfig.username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_username_hint)) },
                singleLine = true
            )

            Text(
                text = stringResource(R.string.settings_password),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = state.draftConfig.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_password_hint)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTest,
                    enabled = state.canTestDraft && !state.isTesting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.settings_test))
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = state.hasDraftChanges && !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.settings_save))
                    }
                }
            }

            // Notice
            state.notice?.let { notice ->
                NoticeCard(notice, onNoticeDismiss)
            }

            HorizontalDivider()

            // Language
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            LanguageSelector(state.language, onLanguageChange)

            HorizontalDivider()

            // Theme
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            ThemeSelector(state.theme, onThemeChange)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NoticeCard(notice: SettingsNotice, onDismiss: () -> Unit) {
    val containerColor = when (notice.type) {
        NoticeType.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
        NoticeType.ERROR -> MaterialTheme.colorScheme.errorContainer
        NoticeType.INFO -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (notice.type) {
        NoticeType.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
        NoticeType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        NoticeType.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(notice.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun LanguageSelector(selected: Language, onSelect: (Language) -> Unit) {
    val options = Language.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { lang ->
            FilterChip(
                selected = selected == lang,
                onClick = { onSelect(lang) },
                label = { Text(lang.label) }
            )
        }
    }
}

@Composable
private fun ThemeSelector(selected: ThemePref, onSelect: (ThemePref) -> Unit) {
    val ctx = LocalContext.current
    val options = listOf(
        ThemePref.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemePref.LIGHT to stringResource(R.string.settings_theme_light),
        ThemePref.DARK to stringResource(R.string.settings_theme_dark)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (theme, label) ->
            FilterChip(
                selected = selected == theme,
                onClick = { onSelect(theme) },
                label = { Text(label) }
            )
        }
    }
}
