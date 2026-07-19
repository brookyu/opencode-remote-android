package ai.opencode.remote.data.api

import ai.opencode.remote.data.models.*
import ai.opencode.remote.normalizeModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class ApiClient(
    private val authInterceptor: AuthInterceptor = AuthInterceptor()
) {
    private var retrofit: Retrofit? = null
    private var apiService: OpenCodeApi? = null
    private var configuredKey: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val client: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun configure(host: String, port: Int, username: String, password: String) {
        val key = "$host:$port:$username:$password"
        if (key == configuredKey && retrofit != null) return
        authInterceptor.setCredentials(username, password)
        val baseUrl = buildBaseUrl(host, port)
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        apiService = retrofit!!.create(OpenCodeApi::class.java)
        configuredKey = key
    }

    private fun buildBaseUrl(host: String, port: Int): String {
        val trimmedHost = host.trim()
        val schemeMatch = Regex("^(https?)://").find(trimmedHost)
        val scheme = schemeMatch?.groupValues?.get(1) ?: "http"
        val cleanHost = schemeMatch?.let { trimmedHost.substring(it.range.last + 1) } ?: trimmedHost
        return "$scheme://$cleanHost:$port/"
    }

    private fun api(): OpenCodeApi = apiService ?: throw IllegalStateException("ApiClient not configured. Call configure() first.")

    suspend fun health(config: ServerConfig): HealthResponse = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().health()
    }

    suspend fun listSessions(config: ServerConfig, directory: String? = null): List<Session> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().listSessions(directory)
    }

    suspend fun listGlobalSessions(config: ServerConfig): List<Session> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        val allSessions = mutableListOf<Session>()
        var cursor: String? = null
        do {
            val response = api().listGlobalSessionsPage(cursor)
            allSessions.addAll(response.body() ?: emptyList())
            cursor = response.headers()["X-Next-Cursor"]
        } while (cursor != null && response.body()?.isNotEmpty() == true)
        allSessions
    }

    suspend fun listStatuses(config: ServerConfig, directory: String? = null): Map<String, SessionStatus> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().listStatuses(directory)
    }

    suspend fun loadMessages(config: ServerConfig, sessionId: String, directory: String? = null): List<MessageEnvelope> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().loadMessages(sessionId, 100, directory)
    }

    suspend fun loadLatestMessage(config: ServerConfig, sessionId: String, directory: String? = null): List<MessageEnvelope> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().loadMessages(sessionId, 1, directory)
    }

    suspend fun loadTodo(config: ServerConfig, sessionId: String, directory: String? = null): List<TodoItem> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().loadTodo(sessionId, directory)
    }

    suspend fun loadDiff(config: ServerConfig, sessionId: String, directory: String? = null): List<DiffFile> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().loadDiff(sessionId, directory)
    }

    suspend fun createSession(config: ServerConfig, title: String?, model: ModelSelection?, directory: String? = null): Session = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        val modelBody = model?.let { CreateSessionModel(providerID = it.providerID, id = it.modelID, variant = it.variant) }
        api().createSession(CreateSessionRequest(title = title, model = modelBody), directory)
    }

    suspend fun deleteSession(config: ServerConfig, sessionId: String, directory: String? = null) = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().deleteSession(sessionId, directory)
    }

    suspend fun sendPrompt(config: ServerConfig, sessionId: String, text: String, directory: String? = null, model: ModelSelection? = null, agentId: String? = null) = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        val modelBody = model?.let { ModelRequestBody(providerID = it.providerID, modelID = it.modelID) }
        val variant = model?.variant?.ifBlank { null }
        val request = PromptRequest(parts = listOf(PromptPart(text = text)), model = modelBody, agent = agentId, variant = variant)
        val response = api().sendPrompt(sessionId, request, directory)
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code()}: ${response.errorBody()?.string() ?: response.message()}")
        }
    }

    suspend fun sendCommand(config: ServerConfig, sessionId: String, command: String, arguments: String, directory: String? = null, model: ModelSelection? = null, agentId: String? = null): MessageEnvelope = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        val modelWire = model?.let { "${it.providerID}/${it.modelID}" }
        api().sendCommand(sessionId, CommandRequest(command = command, arguments = arguments, agent = agentId, model = modelWire, variant = model?.variant), directory)
    }

    suspend fun abortSession(config: ServerConfig, sessionId: String, directory: String? = null) = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().abortSession(sessionId, AbortRequest(), directory)
    }

    suspend fun listCommands(config: ServerConfig): List<CommandInfo> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().listCommands()
    }

    suspend fun listAgents(config: ServerConfig, directory: String? = null): List<AgentOption> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().listAgents(directory).filter { it.id.isNotEmpty() && it.hidden != true }
    }

    suspend fun listModels(config: ServerConfig, directory: String? = null): List<ModelOption> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        val response = api().listProviders(directory)
        response.providers.flatMap { provider ->
            val defaultModel = response.default?.get(provider.id)
            provider.models.entries.flatMap { (modelId, model) ->
                val base = ModelOption(
                    providerID = provider.id,
                    providerName = provider.name ?: provider.id,
                    modelID = model.id ?: modelId,
                    modelName = model.name ?: model.id ?: modelId,
                    status = model.status,
                    contextLimit = model.limit?.context,
                    outputLimit = model.limit?.output,
                    tools = model.capabilities?.toolcall == true || model.capabilities?.tools == true,
                    attachments = model.capabilities?.attachment == true,
                    isDefault = defaultModel != null && (defaultModel == modelId || normalizeModelKey(defaultModel) == normalizeModelKey(modelId) || normalizeModelKey(defaultModel) == normalizeModelKey(model.name ?: ""))
                )
                val variantIds = model.variants?.keys?.toList() ?: emptyList()
                listOf(base) + variantIds.map { v ->
                    base.copy(variant = v, isDefault = false)
                }
            }
        }
    }

    suspend fun loadPath(config: ServerConfig, directory: String? = null): PathInfo = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().loadPath(directory)
    }

    suspend fun listFiles(config: ServerConfig, path: String, directory: String? = null): List<FileEntry> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().listFiles(path, directory)
    }

    suspend fun getFileContent(config: ServerConfig, path: String, directory: String? = null): FileContent = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().getFileContent(path, directory)
    }

    suspend fun loadProjectCurrent(config: ServerConfig, directory: String? = null): ProjectCurrent = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().loadProjectCurrent(directory)
    }

    suspend fun loadVcs(config: ServerConfig, directory: String? = null): VcsStatus = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().loadVcs(directory)
    }

    suspend fun loadFileStatus(config: ServerConfig, directory: String? = null): List<FileStatusEntry> = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        api().loadFileStatus(directory)
    }
}
