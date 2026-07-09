package ai.opencode.remote

import android.content.Context
import ai.opencode.remote.data.models.ModelSelection

enum class Language(val code: String, val label: String) {
    EN("en", "English"),
    IT("it", "Italiano"),
    ZH_TW("zh-TW", "繁體中文");

    companion object {
        fun fromCode(code: String): Language {
            return when {
                code == "it" || code.lowercase().startsWith("it") -> IT
                code == "zh-TW" || code.lowercase().startsWith("zh") -> ZH_TW
                else -> EN
            }
        }
    }
}

enum class ThemePref(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String): ThemePref {
            return when (key) {
                "light" -> LIGHT
                "dark" -> DARK
                else -> SYSTEM
            }
        }
    }
}

object I18n {
    private var currentLanguage: Language = Language.EN

    fun setLanguage(lang: Language) {
        currentLanguage = lang
    }

    fun currentLanguage(): Language = currentLanguage

    fun t(context: Context, key: String, vararg params: Pair<String, Any>): String {
        val resId = context.resources.getIdentifier(key.replace(".", "_"), "string", context.packageName)
        val template = if (resId != 0) context.getString(resId) else key
        return if (params.isEmpty()) template else formatTemplate(template, params)
    }

    fun t(context: Context, key: String): String {
        val resId = context.resources.getIdentifier(key.replace(".", "_"), "string", context.packageName)
        return if (resId != 0) context.getString(resId) else key
    }

    private fun formatTemplate(template: String, params: Array<out Pair<String, Any>>): String {
        var result = template
        params.forEach { (name, value) ->
            result = result.replace("{$name}", value.toString())
        }
        return result
    }
}

fun modelKey(model: ModelSelection): String {
    return listOf(model.providerID, model.modelID, model.variant ?: "").map { java.net.URLEncoder.encode(it, "UTF-8") }.joinToString("|")
}

fun modelFromKey(value: String?): ModelSelection? {
    if (value.isNullOrBlank()) return null
    val parts = value.split("|").map { java.net.URLDecoder.decode(it, "UTF-8") }
    if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
    return ModelSelection(providerID = parts[0], modelID = parts[1], variant = if (parts.size > 2 && parts[2].isNotBlank()) parts[2] else null)
}

fun sameModel(a: ModelSelection?, b: ModelSelection?): Boolean {
    return a != null && b != null && a.providerID == b.providerID && a.modelID == b.modelID && (a.variant ?: "") == (b.variant ?: "")
}

fun formatTime(epoch: Long): String {
    if (epoch <= 0) return "-"
    return java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epoch))
}

fun formatLimit(value: Long?): String {
    value ?: return "-"
    return when {
        value >= 1_000_000 -> "${Math.round(value.toDouble() / 1_000_000)}M"
        value >= 1_000 -> "${Math.round(value.toDouble() / 1_000)}K"
        else -> value.toString()
    }
}

fun extractText(msg: ai.opencode.remote.data.models.MessageEnvelope): String {
    val text = msg.parts
        .filter { it.type == "text" && !it.text.isNullOrBlank() }
        .joinToString("\n") { it.text!! }
        .trim()
    if (text.isNotEmpty()) return text
    val errMsg = msg.info.error?.data?.message
    if (!errMsg.isNullOrBlank()) return "⚠️ $errMsg"
    return ""
}

fun normalizeMessageMarkdown(text: String): String {
    return if (text.contains("\n")) text else text.replace(Regex("\\s-\\s(?=\\S)"), "\n- ")
}

fun parentDirectory(path: String): String? {
    if (path.isEmpty() || path == "/") return null
    val normalized = path.replace(Regex("[/\\\\]+$"), "")
    val separator = if (normalized.contains("\\")) "\\" else "/"
    val index = normalized.lastIndexOf(separator)
    return when {
        index <= 0 -> if (separator == "/") "/" else null
        else -> normalized.substring(0, index)
    }
}
