package com.famguard.controller.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.famguard.controller.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val releaseName: String,
    val changelog: String,
    val downloadUrl: String,
    val fileName: String,
    val isNewer: Boolean
)

object UpdateManager {
    private const val GITHUB_REPO = "alwin2134/FamGuard"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdates(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(RELEASES_API_URL)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        return@withContext Result.success(null) // No releases published yet
                    }
                    return@withContext Result.failure(Exception("GitHub API error HTTP ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
                val releaseObj = json.parseToJsonElement(bodyStr).jsonObject

                val tagName = releaseObj["tag_name"]?.jsonPrimitive?.content ?: ""
                val releaseName = releaseObj["name"]?.jsonPrimitive?.content ?: tagName
                val changelog = releaseObj["body"]?.jsonPrimitive?.content ?: "Maintenance and stability improvements."

                val assets = releaseObj["assets"]?.jsonArray ?: JsonArray(emptyList())
                var targetDownloadUrl = ""
                var targetFileName = "famguard-controller.apk"

                for (asset in assets) {
                    val aObj = asset.jsonObject
                    val aName = aObj["name"]?.jsonPrimitive?.content ?: ""
                    val downloadUrl = aObj["browser_download_url"]?.jsonPrimitive?.content ?: ""

                    if (aName.contains("controller", ignoreCase = true) && aName.endsWith(".apk", ignoreCase = true)) {
                        targetDownloadUrl = downloadUrl
                        targetFileName = aName
                        break
                    } else if (aName.endsWith(".apk", ignoreCase = true) && targetDownloadUrl.isEmpty()) {
                        targetDownloadUrl = downloadUrl
                        targetFileName = aName
                    }
                }

                val cleanTag = tagName.removePrefix("v").trim()
                val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                val isNewer = isVersionNewer(cleanTag, currentVersion)

                if (targetDownloadUrl.isNotEmpty()) {
                    Result.success(
                        UpdateInfo(
                            tagName = tagName,
                            releaseName = releaseName,
                            changelog = changelog,
                            downloadUrl = targetDownloadUrl,
                            fileName = targetFileName,
                            isNewer = isNewer
                        )
                    )
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(downloadUrl).get().build()
            val destFile = File(context.cacheDir, fileName)

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed HTTP ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty download body"))
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress(downloadedBytes.toFloat() / totalBytes)
                            }
                        }
                    }
                }
            }

            // Install APK
            withContext(Dispatchers.Main) {
                installApk(context, destFile)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
