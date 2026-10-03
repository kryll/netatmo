package com.arsys.netatmo.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.arsys.netatmo.BuildConfig
import com.arsys.netatmo.data.api.GitHubApiService
import com.arsys.netatmo.data.model.GitHubRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val release: GitHubRelease) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
    object Installing : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubApiService: GitHubApiService
) {
    companion object {
        private const val REPO_OWNER = "kryll"
        private const val REPO_NAME = "netatmo"
    }

    suspend fun checkForUpdates(): UpdateStatus {
        return try {
            val response = gitHubApiService.getLatestRelease(REPO_OWNER, REPO_NAME)
            when {
                response.isSuccessful -> {
                    val release = response.body()
                        ?: return UpdateStatus.Error("Respuesta vacía del servidor")
                    if (release.versionCode > BuildConfig.VERSION_CODE) {
                        UpdateStatus.UpdateAvailable(release)
                    } else {
                        UpdateStatus.UpToDate
                    }
                }
                response.code() == 404 -> UpdateStatus.UpToDate
                else -> UpdateStatus.Error("Error del servidor (${response.code()})")
            }
        } catch (e: Exception) {
            UpdateStatus.Error(e.message ?: "Error de conexión")
        }
    }

    suspend fun downloadApk(
        release: GitHubRelease,
        onProgress: (Float) -> Unit
    ): Result<File> {
        val downloadUrl = release.downloadUrl
            ?: return Result.failure(Exception("No hay APK disponible en esta versión"))

        return try {
            val apkFile = File(context.cacheDir, "update_${release.tagName}.apk")
            withContext(Dispatchers.IO) {
                var connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                // GitHub redirects to CDN; follow manually if needed
                if (connection.responseCode in 301..302) {
                    val redirectUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    connection = URL(redirectUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 30_000
                    connection.readTimeout = 60_000
                    connection.connect()
                }

                val total = connection.contentLength.toLong()
                var downloaded = 0L

                apkFile.outputStream().use { out ->
                    connection.inputStream.use { input ->
                        val buf = ByteArray(32768)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) onProgress(downloaded.toFloat() / total)
                        }
                    }
                }
                connection.disconnect()
            }
            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        // Grant read permission to every app that could handle the install intent
        context.packageManager
            .queryIntentActivities(intent, 0)
            .forEach { info ->
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        context.startActivity(intent)
    }
}
