package ai.opencode.remote.data.api

import ai.opencode.remote.data.models.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface OpenCodeApi {

    @GET("global/health")
    suspend fun health(): HealthResponse

    @GET("session")
    suspend fun listSessions(
        @Query("directory") directory: String? = null
    ): List<Session>

    @GET("experimental/session")
    suspend fun listGlobalSessionsPage(
        @Query("cursor") cursor: String? = null
    ): Response<List<Session>>

    @GET("session/status")
    suspend fun listStatuses(
        @Query("directory") directory: String? = null
    ): Map<String, SessionStatus>

    @GET("session/{id}/message")
    suspend fun loadMessages(
        @Path("id") sessionId: String,
        @Query("limit") limit: Int = 100,
        @Query("directory") directory: String? = null
    ): List<MessageEnvelope>

    @GET("session/{id}/todo")
    suspend fun loadTodo(
        @Path("id") sessionId: String,
        @Query("directory") directory: String? = null
    ): List<TodoItem>

    @GET("session/{id}/diff")
    suspend fun loadDiff(
        @Path("id") sessionId: String,
        @Query("directory") directory: String? = null
    ): List<DiffFile>

    @POST("session")
    suspend fun createSession(
        @Body body: CreateSessionRequest,
        @Query("directory") directory: String? = null
    ): Session

    @DELETE("session/{id}")
    suspend fun deleteSession(
        @Path("id") sessionId: String,
        @Query("directory") directory: String? = null
    ): Response<Unit>

    @POST("session/{id}/prompt_async")
    suspend fun sendPrompt(
        @Path("id") sessionId: String,
        @Body body: PromptRequest,
        @Query("directory") directory: String? = null
    ): Response<Unit>

    @POST("session/{id}/command")
    suspend fun sendCommand(
        @Path("id") sessionId: String,
        @Body body: CommandRequest,
        @Query("directory") directory: String? = null
    ): MessageEnvelope

    @POST("session/{id}/abort")
    suspend fun abortSession(
        @Path("id") sessionId: String,
        @Body body: AbortRequest = AbortRequest(),
        @Query("directory") directory: String? = null
    ): Response<Unit>

    @GET("command")
    suspend fun listCommands(): List<CommandInfo>

    @GET("agent")
    suspend fun listAgents(
        @Query("directory") directory: String? = null
    ): List<AgentOption>

    @GET("config/providers")
    suspend fun listProviders(
        @Query("directory") directory: String? = null
    ): ConfigProvidersResponse

    @GET("path")
    suspend fun loadPath(
        @Query("directory") directory: String? = null
    ): PathInfo

    @GET("file")
    suspend fun listFiles(
        @Query("path") path: String,
        @Query("directory") directory: String? = null
    ): List<FileEntry>

    @GET("project/current")
    suspend fun loadProjectCurrent(
        @Query("directory") directory: String? = null
    ): ProjectCurrent

    @GET("vcs")
    suspend fun loadVcs(
        @Query("directory") directory: String? = null
    ): VcsStatus

    @GET("file/status")
    suspend fun loadFileStatus(
        @Query("directory") directory: String? = null
    ): List<FileStatusEntry>
}
