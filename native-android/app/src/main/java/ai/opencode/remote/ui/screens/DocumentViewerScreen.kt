package ai.opencode.remote.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Determine if a file path is a markdown file (by extension).
 */
private fun isMarkdownFile(filePath: String): Boolean {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return ext in setOf("md", "markdown", "mdown", "mdwn", "mkd")
}

/**
 * Determine if a file path is a source code file (by extension).
 */
private fun isSourceCodeFile(filePath: String): Boolean {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return ext in setOf(
        "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
        "go", "rs", "swift", "c", "cpp", "h", "hpp",
        "rb", "php", "sh", "bash", "zsh", "yaml", "yml",
        "json", "xml", "html", "css", "scss", "sql",
        "toml", "ini", "cfg", "conf", "gradle",
        "scala", "dart", "lua", "r", "m", "mm",
        "pl", "pm", "ps1", "bat", "cmd"
    )
}

/**
 * Get a display-friendly language name from a file extension.
 */
private fun languageName(filePath: String): String {
    return when (filePath.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "py" -> "Python"
        "js" -> "JavaScript"
        "ts", "tsx" -> "TypeScript"
        "jsx" -> "React JSX"
        "go" -> "Go"
        "rs" -> "Rust"
        "swift" -> "Swift"
        "c" -> "C"
        "cpp", "cc" -> "C++"
        "h", "hpp" -> "C/C++ Header"
        "rb" -> "Ruby"
        "php" -> "PHP"
        "sh", "bash", "zsh" -> "Shell"
        "yaml", "yml" -> "YAML"
        "json" -> "JSON"
        "xml" -> "XML"
        "html" -> "HTML"
        "css", "scss" -> "CSS"
        "sql" -> "SQL"
        "toml" -> "TOML"
        "gradle" -> "Gradle"
        "md", "markdown" -> "Markdown"
        else -> filePath.substringAfterLast('.', "").uppercase()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    filePath: String,
    fileContent: String,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit = {}
) {
    val fileName = filePath.split('/').lastOrNull() ?: filePath
    val isMd = isMarkdownFile(filePath)
    val isCode = isSourceCodeFile(filePath)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            filePath,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Icon(
                        if (isMd) Icons.Filled.Description else Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Loading…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                "Failed to load file",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onRetry) {
                                Text("Retry")
                            }
                        }
                    }
                }
                isMd -> {
                    val ctx = LocalContext.current
                    // Render markdown with the same wrapping used in chat messages.
                    // The CompositionLocalProvider ensures links are tappable.
                    CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                        override fun openUri(uri: String) {
                            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                } catch (_: Exception) { }
                            }
                        }
                    }) {
                        Markdown(
                            content = fileContent,
                            colors = markdownColor(
                                text = MaterialTheme.colorScheme.onSurface,
                                codeText = MaterialTheme.colorScheme.primary,
                                inlineCodeText = MaterialTheme.colorScheme.tertiary,
                                linkText = MaterialTheme.colorScheme.primary,
                                codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                                inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant,
                                dividerColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            typography = markdownTypography(
                                text = MaterialTheme.typography.bodyMedium,
                                h1 = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                h2 = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                h3 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                code = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                inlineCode = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        )
                    }
                }
                isCode -> {
                    // Source code: show with monospace font and language badge
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(0.dp, 0.dp, 12.dp, 0.dp)
                        ) {
                            Text(
                                languageName(filePath),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            fileContent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                else -> {
                    // Fallback: show content as plain monospace text
                    Text(
                        fileContent,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }
}
