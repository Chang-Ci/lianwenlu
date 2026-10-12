package com.lianwenqi.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.lianwenqi.app.data.entity.Book
import com.lianwenqi.app.data.entity.Chapter
import java.io.File
import java.io.FileOutputStream

/** 工具箱：分享 / 导出章节与整本 */
object ExportUtil {

    private fun exportDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(60).ifBlank { "untitled" }

    fun chapterToTxt(context: Context, book: Book, chapter: Chapter): File {
        val sb = StringBuilder()
        sb.append(book.title).append('\n')
        sb.append(chapter.title).append('\n')
        if (chapter.summary.isNotBlank()) {
            sb.append("【梗概】").append(chapter.summary).append("\n\n")
        }
        sb.append(chapter.content)
        val file = File(exportDir(context), "${safeName(chapter.title)}.txt")
        file.writeText(sb.toString())
        return file
    }

    /** 以 HTML 形式写入 .doc，Word / WPS 可直接打开 */
    fun chapterToWord(context: Context, book: Book, chapter: Chapter): File {
        val html = buildDocHtml(book.title, listOf(chapter), includeSummary = true)
        val file = File(exportDir(context), "${safeName(chapter.title)}.doc")
        file.writeText(html)
        return file
    }

    fun bookToTxt(context: Context, book: Book, chapters: List<Chapter>, singleFile: Boolean): File {
        val dir = exportDir(context)
        return if (singleFile) {
            val sb = StringBuilder()
            sb.append(book.title).append("\n\n")
            if (book.intro.isNotBlank()) sb.append(book.intro).append("\n\n")
            chapters.forEach { c ->
                // 整本导出不包含章节梗概
                sb.append(c.title).append('\n')
                sb.append(c.content).append("\n\n")
            }
            val f = File(dir, "${safeName(book.title)}.txt")
            f.writeText(sb.toString())
            f
        } else {
            val folder = File(dir, safeName(book.title)).apply { mkdirs() }
            chapters.forEachIndexed { i, c ->
                File(folder, "%03d_%s.txt".format(i + 1, safeName(c.title))).writeText(
                    c.title + "\n\n" + c.content
                )
            }
            folder
        }
    }

    fun bookToWord(context: Context, book: Book, chapters: List<Chapter>, singleFile: Boolean): File {
        val dir = exportDir(context)
        return if (singleFile) {
            val f = File(dir, "${safeName(book.title)}.doc")
            f.writeText(buildDocHtml(book.title, chapters, includeSummary = false))
            f
        } else {
            val folder = File(dir, safeName(book.title)).apply { mkdirs() }
            chapters.forEachIndexed { i, c ->
                File(folder, "%03d_%s.doc".format(i + 1, safeName(c.title)))
                    .writeText(buildDocHtml(c.title, listOf(c), includeSummary = false))
            }
            folder
        }
    }

    private fun buildDocHtml(title: String, chapters: List<Chapter>, includeSummary: Boolean): String {
        val body = StringBuilder()
        chapters.forEach { c ->
            body.append("<h2>").append(escape(c.title)).append("</h2>\n")
            if (includeSummary && c.summary.isNotBlank()) {
                body.append("<p><i>【梗概】").append(escape(c.summary)).append("</i></p>\n")
            }
            c.content.split("\n").forEach { line ->
                body.append("<p style=\"text-indent:2em;line-height:1.5\">")
                    .append(escape(line)).append("</p>\n")
            }
        }
        return """
            <html xmlns:o="urn:schemas-microsoft-com:office:office"
                  xmlns:w="urn:schemas-microsoft-com:office:word"
                  xmlns="http://www.w3.org/TR/REC-html40">
            <head><meta charset="utf-8"><title>${escape(title)}</title></head>
            <body><h1>${escape(title)}</h1>$body</body></html>
        """.trimIndent()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** 分享章节为长图 */
    fun chapterToImage(
        context: Context,
        book: Book,
        chapter: Chapter,
        width: Int = 1080
    ): File {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 56f; isFakeBoldText = true
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222"); textSize = 40f
        }
        val padding = 60f
        val availWidth = width - padding * 2

        val bodyText = chapter.content
        val bodyLines = wrap(bodyText, bodyPaint, availWidth)

        val titleLines = wrap(book.title + "\n" + chapter.title, titlePaint, availWidth)
        val lineHeight = 64f
        val bodyLineHeight = 58f
        val height = (padding * 2 + titleLines.size * 76f + bodyLines.size * bodyLineHeight)
            .toInt().coerceAtLeast(600)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        var y = padding + 56f
        titleLines.forEach { line ->
            canvas.drawText(line, padding, y, titlePaint); y += 76f
        }
        y += 30f
        bodyLines.forEach { line ->
            canvas.drawText(line, padding, y, bodyPaint); y += bodyLineHeight
        }

        val file = File(exportDir(context), "${safeName(chapter.title)}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private fun wrap(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        text.split("\n").forEach { raw ->
            if (raw.isEmpty()) {
                out.add("")
                return@forEach
            }
            var start = 0
            while (start < raw.length) {
                val count = paint.breakText(raw, start, raw.length, true, maxWidth, null)
                if (count <= 0) break
                out.add(raw.substring(start, start + count))
                start += count
            }
        }
        return out
    }

    fun shareFile(context: Context, file: File, mime: String, chooserTitle: String = "分享") {
        val real = if (file.isDirectory) zipFolder(file) else file
        val realMime = if (file.isDirectory) "application/zip" else mime
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", real
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = realMime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /** 目录打包为 zip，便于分享「所有章节」导出结果 */
    fun zipFolder(folder: File): File {
        val zip = File(folder.parentFile, "${folder.name}.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { zos ->
            folder.walkTopDown().filter { it.isFile }.forEach { f ->
                val entryName = f.relativeTo(folder).path.replace('\\', '/')
                zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zip
    }
}