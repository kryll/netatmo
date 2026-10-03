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
    val apkAsset: GitHubAsset? get() = assets.firstOrNull { it.name.endsWith(".apk") }
    val downloadUrl: String? get() = apkAsset?.browserDownloadUrl
    val apkSizeMb: String get() = apkAsset?.let { "%.1f MB".format(it.size / 1_048_576.0) } ?: ""
    val displayName: String get() = if (name.isNotBlank()) name else tagName
}

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("size") val size: Long
)
