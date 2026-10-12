package com.lianwenqi.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** 封面等本地文件保存 */
object FileUtil {
    fun copyToInternal(context: Context, uri: Uri, prefix: String = "cover"): String? {
        return runCatching {
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val target = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        }.getOrNull()
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}