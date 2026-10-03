package com.arsys.netatmo.data.model

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val body: String?,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
) {
    val versionCode: Int get() = tagName.removePrefix("v").toIntOrNull() ?: 0
    val downloadUrl: String? get() = assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
    val displayName: String get() = if (name.isNotBlank()) name else tagName
}

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("size") val size: Long
)
