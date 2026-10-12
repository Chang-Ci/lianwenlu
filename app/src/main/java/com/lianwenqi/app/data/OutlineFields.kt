package com.lianwenqi.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 大纲条目第一项统一使用的标签名（该项只读，不可改名、不可删除） */
const val OUTLINE_HEAD_LABEL = "显示标签"

@Serializable
private data class OutlineFieldDto(val n: String = "", val v: String = "")

private val outlineJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val outlineFieldsSerializer = ListSerializer(OutlineFieldDto.serializer())

/**
 * 把「标签 → 内容」列表编码为可安全存库的字符串。
 * 采用 JSON 存储，内容里的冒号、换行都能原样保留，
 * 解析时不会再被误拆成新的项（旧版「标签：内容」纯文本格式做不到这一点）。
 */
fun encodeOutlineFields(fields: List<Pair<String, String>>): String =
    outlineJson.encodeToString(
        outlineFieldsSerializer,
        fields.filter { it.first.isNotBlank() }.map { OutlineFieldDto(it.first, it.second) }
    )

/**
 * 解析大纲内容为「标签 → 内容」列表。
 * 新数据为 JSON；旧数据为纯文本「标签：内容」，做兼容回落。
 */
fun decodeOutlineFields(content: String): List<Pair<String, String>> {
    if (content.isBlank()) return emptyList()
    val trimmed = content.trim()
    if (trimmed.startsWith("[")) {
        runCatching {
            return outlineJson.decodeFromString(outlineFieldsSerializer, trimmed).map { it.n to it.v }
        }
    }
    return decodeLegacyOutlineFields(content)
}

/** 兼容旧版纯文本格式：含冒号的行开启新项，其余非空行并入上一项内容 */
private fun decodeLegacyOutlineFields(content: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    // 记录项之间尚未归入任何项的空行数，遇到续行时按原样补回
    var blanks = 0
    content.lines().forEach { raw ->
        val line = raw.trimEnd()
        val i = line.indexOfAny(charArrayOf('：', ':'))
        when {
            i > 0 -> {
                out.add(line.substring(0, i).trim() to line.substring(i + 1).trim())
                blanks = 0
            }

            line.isBlank() -> if (out.isNotEmpty()) blanks++
            out.isEmpty() -> out.add(line.trim() to "")
            else -> {
                val last = out.removeAt(out.lastIndex)
                out.add(last.first to (last.second + "\n".repeat(blanks + 1) + line))
                blanks = 0
            }
        }
    }
    return out
}


