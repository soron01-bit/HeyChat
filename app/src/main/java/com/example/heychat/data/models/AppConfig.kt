package com.example.heychat.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    @SerialName("latest_version_code") val latestVersionCode: Int = 1,
    @SerialName("latest_version_name") val latestVersionName: String = "1.0",
    @SerialName("force_update") val forceUpdate: Boolean = false,
    @SerialName("apk_download_url") val apkDownloadUrl: String = "",
    @SerialName("update_message") val updateMessage: String = "A new version of Hey Chat is available! Please update to get the latest features and bug fixes."
)
