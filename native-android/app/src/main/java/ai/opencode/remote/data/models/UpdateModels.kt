package ai.opencode.remote.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String = "",
    val minVersionCode: Int = 0,
    val checksum: String = ""
)
