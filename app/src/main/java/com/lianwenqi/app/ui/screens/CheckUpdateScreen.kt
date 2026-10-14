package com.lianwenqi.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianwenqi.app.AppContainer
import com.lianwenqi.app.ui.appViewModel
import com.lianwenqi.app.update.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CheckUpdateViewModel(private val container: AppContainer) : ViewModel() {

    private val checker = container.updateChecker

    /** 当前已安装版本 */
    val currentVersion: String = checker.currentVersionName()

    val checking = MutableStateFlow(false)
    val result = MutableStateFlow<UpdateCheckResult?>(null)

    val downloading = MutableStateFlow(false)
    /** 下载进度（已下载字节, 总字节；总字节为 0 表示未知） */
    val downloadProgress = MutableStateFlow(0L to 0L)

    /** 已下载好的安装包（存在即可再次调起安装器） */
    val downloadedApk = MutableStateFlow<File?>(null)

    /** 一次性提示（下载完成 / 安装失败等） */
    val message = MutableStateFlow<String?>(null)

    fun clearMessage() {
        message.value = null
    }

    /** 检查更新：请求 GitHub 最新发布并与当前版本对比 */
    fun check() = viewModelScope.launch {
        checking.value = true
        result.value = null
        downloadedApk.value = null
        message.value = null
        downloadProgress.value = 0L to 0L
        result.value = checker.check()
        checking.value = false
    }

    /** 下载最新安装包，下载完成后自动调起系统安装器 */
    fun download() = viewModelScope.launch {
        val info = (result.value as? UpdateCheckResult.Newer)?.info ?: return@launch
        downloading.value = true
        message.value = null
        downloadProgress.value = 0L to 0L
        try {
            val file = checker.downloadApk(info) { read, total ->
                downloadProgress.value = read to total
            }
            downloadedApk.value = file
            install()
        } catch (e: Throwable) {
            message.value = e.message ?: "下载失败"
        } finally {
            downloading.value = false
        }
    }

    /** 调起系统安装器 */
    fun install() {
        val file = downloadedApk.value ?: return
        val ok = runCatching { container.context.startActivity(checker.installIntent(file)) }.isSuccess
        if (!ok) message.value = "无法调起安装器，请先在系统设置中允许本应用「安装未知应用」"
    }

    /** 打开「安装未知应用」授权页 */
    fun openUnknownSourceSettings() {
        runCatching { container.context.startActivity(checker.unknownSourceSettingsIntent()) }
    }
}

/**
 * 检查更新：请求本仓库 GitHub Releases 的最新发布，对比当前版本；
 * 有新版本时可一键下载安装包并调起系统安装器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckUpdateScreen(onBack: () -> Unit) {
    val vm = appViewModel { CheckUpdateViewModel(it) }
    val checking by vm.checking.collectAsState()
    val result by vm.result.collectAsState()
    val downloading by vm.downloading.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val apk by vm.downloadedApk.collectAsState()
    val message by vm.message.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("检查更新") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前版本", Modifier.weight(1f))
                Text(vm.currentVersion, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.check() },
                enabled = !checking && !downloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checking) "检查中…" else "检查更新")
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            when (val r = result) {
                null -> Text(
                    "点击上方按钮从 GitHub 获取最新版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                UpdateCheckResult.UpToDate -> Text(
                    "已是最新版本",
                    color = MaterialTheme.colorScheme.primary
                )

                is UpdateCheckResult.Failed -> Text(
                    r.message,
                    color = MaterialTheme.colorScheme.error
                )

                is UpdateCheckResult.Newer -> {
                    val info = r.info
                    Text(
                        "发现新版本 v${info.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    if (info.releaseNotes.isNotBlank()) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("更新说明", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    info.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (info.apkUrl == null) {
                        Text(
                            "该版本未提供安装包，请前往发布页下载",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (downloading) {
                        val (read, total) = progress
                        val fraction = if (total > 0) (read.toFloat() / total) else 0f
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (total > 0) {
                                "下载中 ${read / 1024 / 1024}M / ${total / 1024 / 1024}M（${(fraction * 100).toInt()}%）"
                            } else {
                                "下载中 ${read / 1024}K"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (info.apkUrl != null) {
                            Button(
                                onClick = { vm.download() },
                                enabled = !downloading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (downloading) "下载中…" else "下载并安装")
                            }
                        }
                        if (apk != null) {
                            OutlinedButton(
                                onClick = { vm.install() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("重新安装")
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.openUnknownSourceSettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("允许安装未知应用")
                        }
                    }
                }
            }

            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(message!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
