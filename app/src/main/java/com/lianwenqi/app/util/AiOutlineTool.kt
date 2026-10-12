package com.lianwenqi.app.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 「写入大纲工具」的固定 id（AI 聊天页多选、工具页开关共用） */
const val AI_TOOL_WRITE_OUTLINE = "write_outline"

/** AI 工具展示信息 */
data class AiToolInfo(val id: String, val name: String, val description: String)

/** 目前可用的全部 AI 工具（以后新增工具往这里加） */
val AI_TOOLS: List<AiToolInfo> = listOf(
    AiToolInfo(
        id = AI_TOOL_WRITE_OUTLINE,
        name = "写入大纲工具",
        description = "AI 生成的人设 / 世界观等可按固定格式解析并写入作品大纲"
    )
)

/** 大纲区块的起止标记：AI 回答 / 粘贴文本中出现该标记即视为可导入内容 */
const val AI_OUTLINE_BEGIN = "<<<AI_OUTLINE_JSON_V1>>>"
const val AI_OUTLINE_END = "<<<END_AI_OUTLINE_JSON_V1>>>"

/** 「复制工具提示词」复制的完整提示词（勾选工具后也会随请求自动发给模型） */
const val AI_OUTLINE_TOOL_PROMPT: String = """当你需要生成可导入的人设或大纲时，输出以下格式：

<<<AI_OUTLINE_JSON_V1>>>
{
  "blocks": [
    {
      "type": "标签",
      "name": "显示名称",
      "fields": [
        {"key": "字段名", "value": "字段值"}
      ]
    }
  ]
}
<<<END_AI_OUTLINE_JSON_V1>>>
"type"指的是所属大类，如人设，主角人设，X家人设，世界观，故事线分类等
"name"指的是该表名称，如姓名，主线等
 "fields": [
        {"key": "字段名", "value": "字段值"}
 ]
是表具体内容
字段名不要包含特殊符号。字段值中如果包含引号或换行，必须按 JSON 规则转义。"""

/** 一个可导入的大纲区块：标签 + 显示名称 + 字段列表 */
data class AiOutlineBlock(
    val type: String,
    val name: String,
    val fields: List<Pair<String, String>>
)

@Serializable
private data class AiOutlineFieldDto(val key: String = "", val value: String = "")

@Serializable
private data class AiOutlineBlockDto(
    val type: String = "",
    val name: String = "",
    val fields: List<AiOutlineFieldDto> = emptyList()
)

@Serializable
private data class AiOutlinePayload(val blocks: List<AiOutlineBlockDto> = emptyList())

private val aiOutlineJson = Json { ignoreUnknownKeys = true }

/**
 * 从文本中解析全部大纲区块（支持一段文本里含多个标记块）。
 * 解析失败的标记块会被跳过；一个都没有时返回空列表。
 */
fun parseAiOutlineBlocks(text: String): List<AiOutlineBlock> {
    val out = mutableListOf<AiOutlineBlock>()
    var from = 0
    while (true) {
        val begin = text.indexOf(AI_OUTLINE_BEGIN, from)
        if (begin < 0) break
        val jsonStart = begin + AI_OUTLINE_BEGIN.length
        val end = text.indexOf(AI_OUTLINE_END, jsonStart)
        if (end < 0) break
        val json = text.substring(jsonStart, end).trim()
        runCatching {
            aiOutlineJson.decodeFromString(AiOutlinePayload.serializer(), json).blocks
        }.getOrDefault(emptyList()).forEach { b ->
            if (b.type.isNotBlank() && b.name.isNotBlank()) {
                out.add(
                    AiOutlineBlock(
                        type = b.type.trim(),
                        name = b.name.trim(),
                        fields = b.fields
                            .map { it.key.trim() to it.value }
                            .filter { it.first.isNotBlank() }
                    )
                )
            }
        }
        from = end + AI_OUTLINE_END.length
    }
    return out
}
