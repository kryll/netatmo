package com.arsys.netatmo.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
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
        private const val TAG = "UpdateRepository"
    }

    suspend fun checkForUpdates(): UpdateStatus {
        if (BuildConfig.DEBUG) Log.d(TAG, "checkForUpdates: inicio. VERSION_CODE instalado=${BuildConfig.VERSION_CODE}")
        return try {
            val response = gitHubApiService.getLatestRelease(REPO_OWNER, REPO_NAME)
            if (BuildConfig.DEBUG) Log.d(TAG, "checkForUpdates: respuesta HTTP ${response.code()}")
            when {
                response.isSuccessful -> {
                    val release = response.body()
                        ?: return UpdateStatus.Error("Respuesta vacía del servidor").also {
                            if (BuildConfig.DEBUG) Log.e(TAG, "checkForUpdates: body null")
                        }
                    if (BuildConfig.DEBUG) Log.d(TAG, "checkForUpdates: tagName=${release.tagName}, versionCode remoto=${release.versionCode}, instalado=${BuildConfig.VERSION_CODE}")
                    if (release.versionCode > BuildConfig.VERSION_CODE) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "checkForUpdates: actualización disponible")
                        UpdateStatus.UpdateAvailable(release)
                    } else {
                        if (BuildConfig.DEBUG) Log.d(TAG, "checkForUpdates: ya está actualizado")
                        UpdateStatus.UpToDate
                    }
                }
                response.code() == 404 -> {
                    if (BuildConfig.DEBUG) Log.w(TAG, "checkForUpdates: 404 - no hay release")
                    UpdateStatus.UpToDate
                }
                else -> {
                    if (BuildConfig.DEBUG) Log.e(TAG, "checkForUpdates: error servidor ${response.code()}")
                    UpdateStatus.Error("Error del servidor (${response.code()})")
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "checkForUpdates: excepción", e)
            UpdateStatus.Error(e.message ?: "Error de conexión")
        }
    }

    suspend fun downloadApk(
        release: GitHubRelease,
        onProgress: (Float) -> Unit
    ): Result<File> {
        val downloadUrl = release.downloadUrl
        if (BuildConfig.DEBUG) Log.d(TAG, "downloadApk: inicio. tagName=${release.tagName}")
        if (downloadUrl == null) {
            if (BuildConfig.DEBUG) Log.e(TAG, "downloadApk: downloadUrl es null — el release no tiene APK adjunto")
            return Result.failure(Exception("No hay APK disponible en esta versión"))
        }

        return try {
            val apkFile = File(context.cacheDir, "updates/update_${release.tagName}.apk")
            if (BuildConfig.DEBUG) Log.d(TAG, "downloadApk: exists=${apkFile.exists()}, parentExists=${apkFile.parentFile?.exists()}")
            apkFile.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

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
                if (BuildConfig.DEBUG) Log.d(TAG, "downloadApk: descarga completa. descargado=$downloaded bytes")
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "downloadApk: descarga OK. Procediendo a instalar")
            Result.success(apkFile)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "downloadApk: excepción durante descarga", e)
            Result.failure(e)
        }
    }

    fun installApk(file: File) {
        if (BuildConfig.DEBUG) Log.d(TAG, "installApk: inicio. exists=${file.exists()}, size=${file.length()}")
        val providerAuthority = "${context.packageName}.provider"
        val uri = try {
            FileProvider.getUriForFile(context, providerAuthority, file)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "installApk: FileProvider.getUriForFile falló", e)
            return
        }
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        // Grant read permission to every app that could handle the install intent
        val resolvers = context.packageManager.queryIntentActivities(intent, 0)
        if (BuildConfig.DEBUG) {
            if (resolvers.isEmpty()) {
                Log.e(TAG, "installApk: NINGÚN resolver encontrado para ACTION_INSTALL_PACKAGE")
            }
        }
        resolvers.forEach { info ->
            context.grantUriPermission(
                info.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "installApk: lanzando startActivity")
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "installApk: startActivity falló", e)
        }
    }
}
