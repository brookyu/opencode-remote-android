package ai.opencode.remote.data.update

import ai.opencode.remote.BuildConfig
import ai.opencode.remote.data.models.UpdateInfo
import android.app.DownloadManager
import android.app.DownloadManager.Request
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    data object NotAvailable : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

class UpdateManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var pendingDownloadId: Long = -1L
    private var pendingUpdateInfo: UpdateInfo? = null

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == pendingDownloadId && id != -1L) {
                    val info = pendingUpdateInfo ?: return
                    handleDownloadComplete(id, info)
                }
            }
        }
    }

    init {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 34) {
            context.registerReceiver(completionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(completionReceiver, filter)
        }
    }

    /** Default update metadata URL — points to the Mac mini via the zenhost domain. */
    companion object {
        /** Update metadata URL — served by nginx on the ZenHost (Aliyun) cloud server
         *  via SSH tunnel to the Mac mini's nginx on port 8081. */
        const val DEFAULT_UPDATE_URL = "http://124.223.197.48:3457/update.json"
    }

    /**
     * Check for an available update by fetching update.json.
     * Returns the result synchronously (but runs network on IO dispatcher).
     */
    suspend fun checkForUpdate(updateUrl: String = DEFAULT_UPDATE_URL): UpdateResult {
        return withContext(Dispatchers.IO) {
            try {
                val request = OkHttpRequest.Builder()
                    .url(updateUrl)
                    .header("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error("Server returned ${response.code}")
                }

                val body = response.body?.string() ?: return@withContext UpdateResult.Error("Empty response")
                val info = json.decodeFromString<UpdateInfo>(body)

                val localCode = BuildConfig.VERSION_CODE
                if (info.versionCode > localCode) {
                    UpdateResult.Available(info)
                } else {
                    UpdateResult.NotAvailable
                }
            } catch (e: Exception) {
                UpdateResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Start downloading the APK using DownloadManager.
     * Stores the download ID and UpdateInfo for later completion handling.
     */
    fun downloadUpdate(info: UpdateInfo) {
        pendingUpdateInfo = info

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val uri = Uri.parse(info.downloadUrl)
        val fileName = "opencode-remote-${info.versionName}.apk"

        val request = Request(uri).apply {
            setTitle("OpenCode Remote Update")
            setDescription("Downloading v${info.versionName}…")
            setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        }

        pendingDownloadId = downloadManager.enqueue(request)
    }

    /**
     * Called when a download matching our pending ID completes.
     * Verifies success and triggers the install intent.
     */
    private fun handleDownloadComplete(downloadId: Long, info: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))

        cursor.use { c ->
            if (c != null && c.moveToFirst()) {
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val fileUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    if (fileUri != null) {
                        installApk(Uri.parse(fileUri))
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    val reason = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    pendingUpdateInfo = null
                    pendingDownloadId = -1L
                }
            }
        }
    }

    /**
     * Install the downloaded APK using a FileProvider URI + ACTION_VIEW intent.
     */
    fun installApk(fileUri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            // Convert file:// URI to content:// URI via FileProvider
            val apkFile = File(fileUri.path ?: return)
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to open installer: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pendingUpdateInfo = null
            pendingDownloadId = -1L
        }
    }

    /** Clean up — call when the app is done with update operations. */
    fun onDestroy() {
        try {
            context.unregisterReceiver(completionReceiver)
        } catch (_: IllegalArgumentException) { }
    }
}
