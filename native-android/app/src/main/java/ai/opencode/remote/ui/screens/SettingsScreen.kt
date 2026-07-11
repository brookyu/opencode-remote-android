package ai.opencode.remote.ui.screens

import ai.opencode.remote.Language
import ai.opencode.remote.ThemePref
import ai.opencode.remote.viewmodel.NoticeType
import ai.opencode.remote.viewmodel.SettingsNotice
import ai.opencode.remote.viewmodel.SettingsUiState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ai.opencode.remote.R

import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onWorkingRootChange: (String) -> Unit,
    onLanguageChange: (Language) -> Unit,
    onThemeChange: (ThemePref) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onNoticeDismiss: () -> Unit,
    onBack: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    var portText by remember(state.draftConfig.port) {
        mutableStateOf(if (state.draftConfig.port > 0) state.draftConfig.port.toString() else "")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabeledField(label = stringResource(R.string.settings_host)) {
                        OutlinedTextField(
                            value = state.draftConfig.host,
                            onValueChange = onHostChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.settings_host_hint)) },
                            singleLine = true
                        )
                    }
                    LabeledField(label = stringResource(R.string.settings_port)) {
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
                    }
                    LabeledField(label = stringResource(R.string.settings_username)) {
                        OutlinedTextField(
                            value = state.draftConfig.username,
                            onValueChange = onUsernameChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.settings_username_hint)) },
                            singleLine = true
                        )
                    }
                    LabeledField(label = stringResource(R.string.settings_password)) {
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
                    }
                    LabeledField(label = stringResource(R.string.settings_working_root_folder)) {
                        OutlinedTextField(
                            value = state.draftWorkingRootDirectory,
                            onValueChange = onWorkingRootChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.settings_working_root_folder_hint)) },
                            singleLine = true
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LabeledField(label = stringResource(R.string.settings_language)) {
                        LanguageSelector(state.language, onLanguageChange)
                    }
                    HorizontalDivider()
                    LabeledField(label = stringResource(R.string.settings_theme)) {
                        ThemeSelector(state.theme, onThemeChange)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                OutlinedButton(
                    onClick = onTest,
                    enabled = state.canTestDraft && !state.isTesting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(stringResource(R.string.settings_test))
                    }
                }
            }

            AnimatedVisibility(
                visible = state.notice != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.notice?.let { NoticeCard(it, onNoticeDismiss) }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
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
    val icon: ImageVector = when (notice.type) {
        NoticeType.SUCCESS -> Icons.Filled.CheckCircle
        NoticeType.ERROR -> Icons.Filled.Warning
        NoticeType.INFO -> Icons.Filled.Info
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null)
            Text(
                notice.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(selected: Language, onSelect: (Language) -> Unit) {
    val options = Language.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, lang ->
            SegmentedButton(
                selected = selected == lang,
                onClick = { onSelect(lang) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(lang.label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(selected: ThemePref, onSelect: (ThemePref) -> Unit) {
    val options = listOf(
        ThemePref.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemePref.LIGHT to stringResource(R.string.settings_theme_light),
        ThemePref.DARK to stringResource(R.string.settings_theme_dark)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (theme, label) ->
            SegmentedButton(
                selected = selected == theme,
                onClick = { onSelect(theme) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) }
            )
        }
    }
}