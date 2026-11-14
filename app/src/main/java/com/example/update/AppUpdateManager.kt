package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Immutable
data class ReleaseUpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val htmlUrl: String,
    val apkSizeBytes: Long
)

@Immutable
data class UpdateUiState(
    val isChecking: Boolean = false,
    val availableUpdate: ReleaseUpdateInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkPath: String? = null,
    val needsInstallPermission: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val statusText: String? = null
)

object AppUpdateManager {

    const val GITHUB_OWNER = "sibasyanya"
    const val GITHUB_REPO = "pillbox"
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    private const val PREFS_NAME = "pillbox_update_prefs"
    private const val KEY_LAST_CHECK_MILLIS = "last_auto_check_millis"
    private const val AUTO_CHECK_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L // 6 часов

    fun cleanupOldApks(context: Context) {
        try {
            val dir = File(context.cacheDir, "updates")
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    fun shouldAutoCheck(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_MILLIS, 0L)
            (System.currentTimeMillis() - lastCheck) >= AUTO_CHECK_INTERVAL_MILLIS
        } catch (_: Exception) {
            true
        }
    }

    fun markAutoChecked(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_CHECK_MILLIS, System.currentTimeMillis()).apply()
        } catch (_: Exception) {
        }
    }

    suspend fun checkForUpdate(currentVersionName: String): Result<ReleaseUpdateInfo?> =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(LATEST_RELEASE_API_URL)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Pillbox-Android-Updater")
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    // В репозитории пока нет опубликованных релизов
                    return@withContext Result.success(null)
                }
                if (code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("Сервер GitHub вернул код $code")
                    )
                }

                val bodyText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(bodyText)
                val tagName = json.optString("tag_name", "").trim()
                val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
                if (cleanRemoteVersion.isEmpty()) {
                    return@withContext Result.success(null)
                }

                if (!isRemoteVersionNewer(currentVersionName, cleanRemoteVersion)) {
                    return@withContext Result.success(null)
                }

                val htmlUrl = json.optString("html_url", RELEASES_PAGE_URL)
                val releaseNotes = json.optString("body", "").trim().ifEmpty {
                    "Доступна новая версия приложения $tagName."
                }

                var apkUrl = ""
                var apkSize = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotEmpty()) {
                            apkUrl = downloadUrl
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                if (apkUrl.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("В релизе $tagName еще не прикреплен APK-файл")
                    )
                }

                Result.success(
                    ReleaseUpdateInfo(
                        versionName = cleanRemoteVersion,
                        tagName = tagName,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkUrl,
                        htmlUrl = htmlUrl,
                        apkSizeBytes = apkSize
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }

    suspend fun downloadUpdateApk(
        context: Context,
        info: ReleaseUpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val safeVersion = info.versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(updatesDir, "pillbox_$safeVersion.apk")
            val tempFile = File(updatesDir, "pillbox_$safeVersion.apk.tmp")

            if (tempFile.exists()) {
                tempFile.delete()
            }

            var currentUrl = info.apkDownloadUrl
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("User-Agent", "Pillbox-Android-Updater")
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank()) break
                    currentUrl = location
                    redirects++
                } else {
                    break
                }
            }

            val finalConn = connection ?: return@withContext Result.failure(
                IllegalStateException("Не удалось подключиться к серверу загрузки")
            )

            if (finalConn.responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Ошибка скачивания APK (код ${finalConn.responseCode})")
                )
            }

            val totalBytes = finalConn.contentLengthLong.let {
                if (it > 0L) it else info.apkSizeBytes
            }

            var downloadedBytes = 0L
            var lastReportedTime = 0L
            var lastReportedProgress = 0f

            finalConn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0L) {
                            val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            val now = System.currentTimeMillis()
                            if (now - lastReportedTime >= 80L || progress - lastReportedProgress >= 0.02f || progress >= 1f) {
                                lastReportedTime = now
                                lastReportedProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(1f)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallUnknownAppsSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isRemoteVersionNewer(currentVersion: String, remoteVersion: String): Boolean {
        val currentParts = currentVersion.removePrefix("v").removePrefix("V")
            .split(".", "-")
            .mapNotNull { it.toIntOrNull() }
        val remoteParts = remoteVersion.removePrefix("v").removePrefix("V")
            .split(".", "-")
            .mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val cur = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > cur) return true
            if (rem < cur) return false
        }
        return false
    }
}
