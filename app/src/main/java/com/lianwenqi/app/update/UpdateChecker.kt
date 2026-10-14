package com.lianwenqi.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 检查更新的结果 */
sealed class UpdateCheckResult {
    /** 有新版本可用 */
    data class Newer(val info: UpdateInfo) : UpdateCheckResult()

    /** 已是最新版本 */
    data object UpToDate : UpdateCheckResult()

    /** 检查失败（网络 / 服务器 / 解析等） */
    data class Failed(val message: String) : UpdateCheckResult()
}

/** 新版本信息 */
data class UpdateInfo(
    /** 最新版本号（已去掉 v 前缀，如 3.1） */
    val versionName: String,
    /** 发布说明（Markdown 原文，按纯文本展示） */
    val releaseNotes: String,
    /** Release 页面地址（浏览器打开） */
    val htmlUrl: String,
    /** 安装包下载地址；发布里没有 apk 附件时为 null */
    val apkUrl: String?,
    /** 安装包文件名 */
    val apkName: String
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GhAsset> = emptyList()
)

@Serializable
private data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = ""
)

/**
 * 检查更新：请求本仓库 GitHub Releases 的最新发布，与当前安装版本对比。
 * 有新版本时可直接把 apk 附件下载到应用缓存目录，再交给系统安装器安装。
 */
class UpdateChecker(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 当前已安装的版本名（如 3.1） */
    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "0" }

    /** 当前已安装的 versionCode（版本名命名不一致时用它比较更可靠） */
    private fun currentVersionCode(): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
    }.getOrDefault(0L)

    /** 请求最新发布并与当前版本对比 */
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LianWenLu-Android")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> UpdateCheckResult.Failed("仓库暂无发布版本")
                    !response.isSuccessful -> UpdateCheckResult.Failed("检查失败：服务器返回 ${response.code}")
                    else -> {
                        val raw = response.body?.string().orEmpty()
                        val release = json.decodeFromString<GhRelease>(raw)
                        val latest = release.tagName.removePrefix("v").removePrefix("V")
                        if (latest.isBlank()) {
                            UpdateCheckResult.Failed("检查失败：发布信息不完整")
                        } else {
                            // 版本号命名历史不一致（3.0 → 3.01 → 3.02 → 3.1），
                            // 优先用发布说明里的 versionCode 比较；拿不到时退回 versionName 分段比较。
                            val remoteCode =
                                Regex("versionCode\\s*(\\d+)").find(release.body)
                                    ?.groupValues?.get(1)?.toLongOrNull()
                            val currentCode = currentVersionCode()
                            val newer = if (remoteCode != null && currentCode > 0) {
                                remoteCode > currentCode
                            } else {
                                isNewer(latest, currentVersionName())
                            }
                            if (!newer) {
                                UpdateCheckResult.UpToDate
                            } else {
                                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", true) }
                                UpdateCheckResult.Newer(
                                    UpdateInfo(
                                        versionName = latest,
                                        releaseNotes = release.body.trim(),
                                        htmlUrl = release.htmlUrl,
                                        apkUrl = apk?.url,
                                        apkName = apk?.name ?: "lianwenlu-$latest.apk"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            UpdateCheckResult.Failed("检查失败：无法连接网络（${e.message ?: "未知错误"}）")
        } catch (e: Throwable) {
            UpdateCheckResult.Failed("检查失败：${e.message ?: "未知错误"}")
        }
    }

    /**
     * 下载安装包到缓存目录（exports 目录已由 FileProvider 暴露）。
     * [onProgress] 回调（已下载字节, 总字节；总字节未知时为 0）。
     */
    suspend fun downloadApk(
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val url = info.apkUrl ?: throw IOException("该版本未提供安装包")
        val request = Request.Builder().url(url).header("User-Agent", "LianWenLu-Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("下载失败：服务器返回 ${response.code}")
            val body = response.body ?: throw IOException("下载失败：响应为空")
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, info.apkName)
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val total = body.contentLength()
                    val buffer = ByteArray(16 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, if (total > 0) total else 0L)
                    }
                }
            }
            file
        }
    }

    /** 把下载好的安装包交给系统安装器（走 FileProvider，需 REQUEST_INSTALL_PACKAGES 权限） */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 安装未知应用授权页（系统未授权时引导用户开启） */
    fun unknownSourceSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 版本号比较：按「.」分段逐段比大小（支持 3.1 与 3.02 这类写法） */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private companion object {
        /** 本仓库坐标：GitHub Releases 即更新源 */
        const val REPO = "Twoforan/lianwenlu"
    }
}
