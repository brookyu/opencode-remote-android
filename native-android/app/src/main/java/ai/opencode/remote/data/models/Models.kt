package ai.opencode.remote.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServerConfig(
    val host: String = "",
    val port: Int = 4096,
    val username: String = "opencode",
    val password: String = ""
)

@Serializable
data class HealthResponse(
    val healthy: Boolean = false,
    val version: String = ""
)

@Serializable
data class ModelSelection(
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String,
    val variant: String? = null
)

@Serializable
data class AgentOption(
    val id: String,
    val name: String,
    val description: String? = null,
    val mode: String = "primary",
    val hidden: Boolean? = false
)

@Serializable
data class ModelOption(
    @SerialName("providerID") val providerID: String,
    val providerName: String,
    @SerialName("modelID") val modelID: String,
    val modelName: String,
    val status: String? = null,
    val contextLimit: Long? = null,
    val outputLimit: Long? = null,
    val tools: Boolean? = null,
    val attachments: Boolean? = null,
    val isDefault: Boolean? = null,
    val variant: String? = null
)

@Serializable
data class SessionTime(
    val created: Long = 0,
    val updated: Long = 0
)

@Serializable
data class SessionSummary(
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: Int = 0
)

@Serializable
data class SessionModel(
    val id: String,
    @SerialName("providerID") val providerID: String,
    val variant: String? = null
)

@Serializable
data class SessionProject(
    val id: String? = null,
    val name: String? = null,
    val worktree: String? = null
)

@Serializable
data class Session(
    val id: String,
    val title: String,
    val directory: String = "",
    val time: SessionTime = SessionTime(),
    val summary: SessionSummary? = null,
    val model: SessionModel? = null,
    val project: SessionProject? = null
)

@Serializable
data class SessionStatus(
    val type: String = "idle",
    val attempt: Int? = null,
    val message: String? = null,
    val next: Long? = null
)

@Serializable
data class MessagePart(
    val id: String,
    val type: String,
    val text: String? = null
)

@Serializable
data class MessageInfo(
    val id: String,
    val role: String,
    @SerialName("sessionID") val sessionID: String,
    val time: MessageTime
)

@Serializable
data class MessageTime(
    val created: Long = 0,
    val completed: Long? = null
)

@Serializable
data class MessageEnvelope(
    val info: MessageInfo,
    val parts: List<MessagePart> = emptyList()
)

@Serializable
data class TodoItem(
    val content: String,
    val status: String,
    val priority: String,
    val id: String
)

@Serializable
data class DiffFile(
    val file: String,
    val additions: Int,
    val deletions: Int
)

@Serializable
data class CommandInfo(
    val name: String,
    val description: String? = null,
    val source: String? = null
)

@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val absolute: String,
    val type: String,
    val ignored: Boolean? = null
)

@Serializable
data class PathInfo(
    val home: String = "",
    val state: String = "",
    val config: String = "",
    val worktree: String = "",
    val directory: String = ""
)

@Serializable
data class ProjectCurrent(
    val name: String? = null,
    val path: String? = null,
    val directory: String? = null,
    val root: String? = null
)

@Serializable
data class VcsStatus(
    val branch: String? = null,
    val status: String? = null,
    val ahead: Int? = null,
    val behind: Int? = null
)

@Serializable
data class FileStatusEntry(
    val path: String? = null,
    val file: String? = null,
    val status: String? = null
)

@Serializable
data class ProviderModel(
    val id: String? = null,
    val name: String? = null,
    val status: String? = null,
    val capabilities: ModelCapabilities? = null,
    val limit: ModelLimit? = null,
    val variants: Map<String, JsonElement>? = null
)

@Serializable
data class ModelCapabilities(
    val attachment: Boolean? = null,
    val toolcall: Boolean? = null,
    val tools: Boolean? = null
)

@Serializable
data class ModelLimit(
    val context: Long? = null,
    val output: Long? = null
)

@Serializable
data class ConfigProvider(
    val id: String,
    val name: String? = null,
    val models: Map<String, ProviderModel> = emptyMap()
)

@Serializable
data class ConfigProvidersResponse(
    val providers: List<ConfigProvider> = emptyList(),
    val default: Map<String, String>? = null
)

@Serializable
data class PromptRequest(
    val parts: List<PromptPart>,
    val model: ModelRequestBody? = null,
    val agent: String? = null,
    val variant: String? = null
)

@Serializable
data class PromptPart(
    val type: String = "text",
    val text: String
)

@Serializable
data class ModelRequestBody(
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String
)

@Serializable
data class CommandRequest(
    val command: String,
    val arguments: String,
    val agent: String? = null,
    val model: String? = null,
    val variant: String? = null
)

@Serializable
data class CreateSessionRequest(
    val title: String? = null,
    val model: CreateSessionModel? = null
)

@Serializable
data class CreateSessionModel(
    @SerialName("providerID") val providerID: String,
    val id: String,
    val variant: String? = null
)

@Serializable
data class AbortRequest(
    val dummy: String = ""
)
