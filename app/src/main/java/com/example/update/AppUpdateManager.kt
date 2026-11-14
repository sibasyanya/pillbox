package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream

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
    const val LATEST_RELEASE_WEB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    private const val JSDELIVR_CHANGELOG_URL =
        "https://cdn.jsdelivr.net/gh/$GITHUB_OWNER/$GITHUB_REPO@main/CHANGELOG.md"
    private const val RAW_GITHUB_CHANGELOG_URL =
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/CHANGELOG.md"
    private const val GHFAST_CHANGELOG_URL =
        "https://ghfast.top/https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/CHANGELOG.md"
    private const val GHFAST_RELEASE_WEB_URL =
        "https://ghfast.top/https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val PROXY_PREFIXES = listOf(
        "",
        "https://ghfast.top/",
        "https://gh-proxy.com/"
    )

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

    private data class DiscoveredRelease(
        val versionName: String,
        val tagName: String,
        val releaseNotes: String,
        val apkDownloadUrl: String,
        val htmlUrl: String,
        val apkSizeBytes: Long
    )

    suspend fun checkForUpdate(currentVersionName: String): Result<ReleaseUpdateInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val discovered = discoverLatestReleaseParallel()
                    ?: return@withContext Result.failure(
                        IllegalStateException("Сервер обновлений временно недоступен. Проверьте подключение к сети.")
                    )

                if (discovered.versionName.isEmpty()) {
                    return@withContext Result.success(null)
                }

                if (!isRemoteVersionNewer(currentVersionName, discovered.versionName)) {
                    return@withContext Result.success(null)
                }

                val notes = discovered.releaseNotes.ifBlank {
                    fetchChangelogNotesFallback(discovered.versionName)
                        ?: "Доступна новая версия приложения ${discovered.tagName}."
                }

                Result.success(
                    ReleaseUpdateInfo(
                        versionName = discovered.versionName,
                        tagName = discovered.tagName,
                        releaseNotes = notes,
                        apkDownloadUrl = discovered.apkDownloadUrl,
                        htmlUrl = discovered.htmlUrl,
                        apkSizeBytes = discovered.apkSizeBytes
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Параллельно опрашивает несколько независимых каналов (редирект GitHub 0 байт,
     * GitHub API с GZIP, зеркало ghfast и глобальный CDN jsDelivr/Raw), возвращая
     * первый успешный ответ без ожидания зависших таймаутов.
     */
    private suspend fun discoverLatestReleaseParallel(): DiscoveredRelease? = supervisorScope {
        val resultDeferred = CompletableDeferred<DiscoveredRelease?>()
        val tasksCount = 5
        val failedCount = AtomicInteger(0)

        fun completeOrFail(candidate: DiscoveredRelease?) {
            if (candidate != null && candidate.versionName.isNotEmpty()) {
                resultDeferred.complete(candidate)
            } else if (failedCount.incrementAndGet() >= tasksCount) {
                resultDeferred.complete(null)
            }
        }

        val jobs = listOf(
            // 1. Быстрый 0-байтовый HTTP 302 редирект с github.com/releases/latest
            launch {
                completeOrFail(fetchViaRedirect(LATEST_RELEASE_WEB_URL))
            },
            // 2. Быстрый 0-байтовый HTTP 302 редирект через зеркало ghfast.top
            launch {
                completeOrFail(fetchViaRedirect(GHFAST_RELEASE_WEB_URL))
            },
            // 3. Официальный GitHub REST API со сжатием GZIP (~1 КБ)
            launch {
                completeOrFail(fetchViaGitHubApi())
            },
            // 4. Глобальный CDN jsDelivr (доступен в РФ даже при блокировках/VPN split-tunnel)
            launch {
                delay(350) // Даем небольшую фору прямому редиректу релиза
                completeOrFail(fetchViaChangelogUrl(JSDELIVR_CHANGELOG_URL))
            },
            // 5. Зеркало CHANGELOG через ghfast / raw.githubusercontent.com
            launch {
                delay(500)
                val res = fetchViaChangelogUrl(GHFAST_CHANGELOG_URL)
                    ?: fetchViaChangelogUrl(RAW_GITHUB_CHANGELOG_URL)
                completeOrFail(res)
            }
        )

        val winner = withTimeoutOrNull(18_000L) {
            resultDeferred.await()
        }
        jobs.forEach { it.cancel() }
        winner
    }

    private fun fetchViaRedirect(endpointUrl: String): DiscoveredRelease? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Pillbox-Android-Updater")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Connection", "close")
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location") ?: ""
            if (code in 300..399 && location.contains("/releases/tag/")) {
                val tag = location.substringAfterLast("/releases/tag/")
                    .substringBefore("?")
                    .substringBefore("#")
                    .trim()
                val cleanVer = tag.removePrefix("v").removePrefix("V").trim()
                if (cleanVer.isNotEmpty()) {
                    val normTag = "v$cleanVer"
                    return DiscoveredRelease(
                        versionName = cleanVer,
                        tagName = normTag,
                        releaseNotes = "",
                        apkDownloadUrl = buildCanonicalApkUrl(normTag),
                        htmlUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/$normTag",
                        apkSizeBytes = 0L
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchViaGitHubApi(): DiscoveredRelease? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                useCaches = false
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("User-Agent", "Pillbox-Android-Updater")
                setRequestProperty("Connection", "close")
            }
            val code = conn.responseCode
            if (code !in 200..299) return null

            val bodyText = readStreamText(conn)
            val json = JSONObject(bodyText)
            val tagName = json.optString("tag_name", "").trim()
            val cleanVer = tagName.removePrefix("v").removePrefix("V").trim()
            if (cleanVer.isEmpty()) return null

            val normTag = "v$cleanVer"
            val htmlUrl = json.optString(
                "html_url",
                "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/$normTag"
            )
            val notes = json.optString("body", "").trim()

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
                apkUrl = buildCanonicalApkUrl(normTag)
            }

            DiscoveredRelease(
                versionName = cleanVer,
                tagName = normTag,
                releaseNotes = notes,
                apkDownloadUrl = apkUrl,
                htmlUrl = htmlUrl,
                apkSizeBytes = apkSize
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchViaChangelogUrl(changelogUrl: String): DiscoveredRelease? {
        var conn: HttpURLConnection? = null
        return try {
            val urlWithBust = "$changelogUrl?t=${System.currentTimeMillis() / 60_000L}"
            conn = (URL(urlWithBust).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                useCaches = false
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Range", "bytes=0-2048")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "Pillbox-Android-Updater")
                setRequestProperty("Connection", "close")
            }
            val code = conn.responseCode
            if (code !in 200..299) return null

            val text = readStreamText(conn)
            val parsed = parseTopReleaseFromChangelog(text) ?: return null
            val cleanVer = parsed.first
            val notes = parsed.second
            val normTag = "v$cleanVer"

            DiscoveredRelease(
                versionName = cleanVer,
                tagName = normTag,
                releaseNotes = notes,
                apkDownloadUrl = buildCanonicalApkUrl(normTag),
                htmlUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/$normTag",
                apkSizeBytes = 0L
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun fetchChangelogNotesFallback(versionName: String): String? = supervisorScope {
        withTimeoutOrNull(3_500L) {
            val d1 = async { fetchViaChangelogUrl(JSDELIVR_CHANGELOG_URL) }
            val d2 = async { fetchViaChangelogUrl(GHFAST_CHANGELOG_URL) }
            val r1 = d1.await()
            if (r1 != null && r1.versionName == versionName && r1.releaseNotes.isNotBlank()) {
                d2.cancel()
                return@withTimeoutOrNull r1.releaseNotes
            }
            val r2 = d2.await()
            if (r2 != null && r2.versionName == versionName && r2.releaseNotes.isNotBlank()) {
                return@withTimeoutOrNull r2.releaseNotes
            }
            null
        }
    }

    private fun parseTopReleaseFromChangelog(markdown: String): Pair<String, String>? {
        val lines = markdown.lines()
        var foundVersion: String? = null
        val notesBuilder = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## [")) {
                if (foundVersion == null) {
                    val ver = trimmed.substringAfter("## [").substringBefore("]").trim()
                        .removePrefix("v").removePrefix("V")
                    if (ver.isNotEmpty()) {
                        foundVersion = ver
                    }
                } else {
                    break
                }
            } else if (foundVersion != null) {
                notesBuilder.appendLine(line)
            }
        }

        val ver = foundVersion ?: return null
        return ver to notesBuilder.toString().trim()
    }

    private fun buildCanonicalApkUrl(tagName: String): String {
        val normTag = if (tagName.startsWith("v")) tagName else "v$tagName"
        return "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$normTag/pillbox-$normTag.apk"
    }

    private fun readStreamText(conn: HttpURLConnection): String {
        val rawStream: InputStream = conn.inputStream
        val encoding = conn.contentEncoding ?: ""
        val stream = if (encoding.contains("gzip", ignoreCase = true)) {
            GZIPInputStream(rawStream)
        } else {
            rawStream
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * Скачивает APK с поддержкой докачки (HTTP Range) при обрывах связи и автоматическим
     * переключением между прямым сервером GitHub и CDN-зеркалами (ghfast.top, gh-proxy.com).
     */
    suspend fun downloadUpdateApk(
        context: Context,
        info: ReleaseUpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val safeVersion = info.versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(updatesDir, "pillbox_$safeVersion.apk")
            val tempFile = File(updatesDir, "pillbox_$safeVersion.apk.part")

            // Если этот APK уже полностью скачан и валиден — сразу возвращаем его
            if (targetFile.exists() && isValidApkArchive(context, targetFile)) {
                onProgress(1f)
                return@withContext Result.success(targetFile)
            }

            val baseApkUrl = info.apkDownloadUrl.ifBlank {
                buildCanonicalApkUrl(info.tagName)
            }

            val candidateUrls = PROXY_PREFIXES.map { prefix ->
                if (prefix.isEmpty() || baseApkUrl.startsWith(prefix)) {
                    baseApkUrl
                } else {
                    "$prefix$baseApkUrl"
                }
            }.distinct()

            var lastError: Exception? = null
            var knownTotalBytes = info.apkSizeBytes

            for (attempt in 0 until 6) {
                val mirrorUrl = candidateUrls[attempt % candidateUrls.size]
                var connection: HttpURLConnection? = null
                try {
                    val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                    connection = openFollowRedirects(
                        initialUrl = mirrorUrl,
                        rangeStartBytes = existingBytes
                    )

                    val responseCode = connection.responseCode
                    val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
                    val isOk = responseCode == HttpURLConnection.HTTP_OK

                    if (!isPartial && !isOk) {
                        throw IllegalStateException("Сервер вернул код $responseCode")
                    }

                    val startOffset = if (isPartial) existingBytes else 0L
                    if (!isPartial && existingBytes > 0L) {
                        tempFile.delete()
                    }

                    val contentLength = connection.contentLengthLong
                    val contentRange = connection.getHeaderField("Content-Range") ?: ""
                    val totalFromRange = contentRange.substringAfterLast("/", "")
                        .trim()
                        .toLongOrNull() ?: 0L

                    val totalBytes = when {
                        totalFromRange > 0L -> totalFromRange
                        contentLength > 0L -> startOffset + contentLength
                        else -> knownTotalBytes
                    }
                    if (totalBytes > 0L) {
                        knownTotalBytes = totalBytes
                    }

                    var downloadedBytes = startOffset
                    var lastReportedTime = 0L
                    var lastReportedProgress = if (totalBytes > 0L) {
                        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    onProgress(lastReportedProgress)

                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile, isPartial).use { output ->
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

                    if (totalBytes > 0L && tempFile.length() < totalBytes) {
                        throw IllegalStateException("Загружено ${tempFile.length()} из $totalBytes байт, докачка...")
                    }

                    if (!isValidApkArchive(context, tempFile)) {
                        tempFile.delete()
                        throw IllegalStateException("Загруженный файл поврежден, повторная загрузка с зеркала...")
                    }

                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }

                    onProgress(1f)
                    return@withContext Result.success(targetFile)
                } catch (e: Exception) {
                    lastError = e
                } finally {
                    connection?.disconnect()
                }
            }

            Result.failure(
                lastError ?: IllegalStateException("Не удалось скачать APK после нескольких попыток")
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openFollowRedirects(
        initialUrl: String,
        rangeStartBytes: Long
    ): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Pillbox-Android-Updater")
                setRequestProperty("Connection", "close")
                if (rangeStartBytes > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStartBytes-")
                }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Пустой редирект при скачивании APK")
                }
                currentUrl = when {
                    location.startsWith("http://") || location.startsWith("https://") -> location
                    location.startsWith("/https://") || location.startsWith("/http://") -> location.removePrefix("/")
                    else -> URL(URL(currentUrl), location).toString()
                }
                redirects++
            } else {
                return conn
            }
        }
        throw IllegalStateException("Слишком много перенаправлений при скачивании APK")
    }

    private fun isValidApkArchive(context: Context, file: File): Boolean {
        if (!file.exists() || file.length() < 500_000L) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val b0 = raf.read()
                val b1 = raf.read()
                val b2 = raf.read()
                val b3 = raf.read()
                if (b0 != 0x50 || b1 != 0x4B || b2 != 0x03 || b3 != 0x04) {
                    return false
                }
            }
            val archiveInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            archiveInfo != null && archiveInfo.packageName == context.packageName
        } catch (_: Exception) {
            true
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
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
