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
import java.net.URLEncoder

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

    suspend fun updateSession(config: ServerConfig, sessionId: String, title: String? = null, model: ModelSelection? = null, directory: String? = null): Session = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        val modelBody = model?.let { CreateSessionModel(providerID = it.providerID, id = it.modelID, variant = it.variant) }
        api().updateSession(sessionId, CreateSessionRequest(title = title, model = modelBody), directory)
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

    companion object {
        /** Public download server URL — Aliyun nginx proxies through SSH tunnel to Mac Mini. */
        const val FILE_SERVER_BASE = "http://124.223.197.48:3457"
    }

    /**
     * Read a file's content from the server's filesystem.
     *
     * Strategies tried in order:
     * 1. Direct HTTP GET on the OpenCode server base URL + file path
     * 2. HTTP GET on the OpenCode server's /file?path=... endpoint
     * 3. Fetch via the download domain using the session directory
     */
    suspend fun readFileContent(config: ServerConfig, filePath: String, directory: String? = null, sessionId: String? = null): String = withContext(Dispatchers.IO) {
        configure(config.host, config.port, config.username, config.password)
        var lastError: String? = null

        val base = buildBaseUrl(config.host, config.port).trimEnd('/')
        val dir = directory?.let { if (it.isBlank()) null else it }

        // Strategy 1: Use the /file/content?path=... API endpoint
        try {
            val fileRes = api().readFileContent(filePath, dir)
            val content = fileRes.content
            if (content != null) {
                return@withContext content
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.toString()
        }

        // Strategy 2: Direct HTTP GET on the server base URL + file path
        val url1 = "$base/${filePath.trimStart('/')}"
        val r1 = tryFetchUrl(url1)
        if (r1 != null && !isHtmlResponse(r1)) return@withContext r1
        if (r1 != null) lastError = "OpenCode server returned web UI"

        // Strategy 3: Send a /read command via the session command API
        if (!sessionId.isNullOrBlank()) {
            try {
                val cmdResp = api().sendCommand(
                    sessionId,
                    CommandRequest(command = "read", arguments = filePath),
                    dir
                )
                val cmdText = cmdResp.parts
                    .filter { it.type == "text" }
                    .joinToString("\n") { it.text ?: "" }
                if (cmdText.isNotBlank() && !isHtmlResponse(cmdText)) {
                    val lines = cmdText.lines()
                    val content = if (lines.size > 1 && lines.first().trimStart().startsWith("/read")) {
                        lines.drop(1).joinToString("\n")
                    } else {
                        cmdText
                    }
                    if (content.isNotBlank()) return@withContext content.trimStart()
                }
            } catch (_: Exception) { }
        }

        // Strategy 4: Use the /file?path=... endpoint (server might return file content)
        val url2 = "$base/file?path=${java.net.URLEncoder.encode(filePath, "UTF-8")}"
        val r2 = tryFetchUrl(url2, isJsonEndpoint = true)
        if (r2 != null && !isHtmlResponse(r2)) return@withContext r2

        // Strategy 4: Try with session directory as a base path
        if (dir != null) {
            val url3 = "$base/${dir.trimEnd('/')}/${filePath.trimStart('/')}"
            val r3 = tryFetchUrl(url3)
            if (r3 != null && !isHtmlResponse(r3)) return@withContext r3
        }

        // Strategy 5: Try the download domain
        if (dir != null) {
            val url4 = "${FILE_SERVER_BASE}/files/${dir.trimEnd('/')}/${filePath.trimStart('/')}"
            val r4 = tryFetchUrl(url4)
            if (r4 != null && !isHtmlResponse(r4)) return@withContext r4
        }

        throw Exception("Cannot read file \"$filePath\" remotely. $lastError")
    }

    private suspend fun tryFetchUrl(url: String, isJsonEndpoint: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", if (isJsonEndpoint) "application/json, text/plain, */*" else "text/plain, text/markdown, */*")
                .build()
            val shortClient = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = shortClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        } catch (_: Exception) {
            null
        }
    }

    private fun isHtmlResponse(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.startsWith("<!DOCTYPE html", true) ||
               trimmed.startsWith("<html", true) ||
               trimmed.startsWith("<head", true)
    }
}
