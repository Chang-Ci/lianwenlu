package com.lianwenqi.app.data

import com.lianwenqi.app.data.entity.OutlineItem
import com.lianwenqi.app.data.entity.defaultOutlineKeys
import com.lianwenqi.app.data.entity.outlineCategoryLabel
import com.lianwenqi.app.util.AiOutlineBlock

/** 一次大纲导入的结果统计 */
data class AiOutlineImportResult(
    /** 成功写入的区块数 */
    val blocks: Int,
    /** 写入的大纲条目数（与区块数相同，保留给以后一块多条的情况） */
    val items: Int,
    /** 本次新建的分类标签名 */
    val newCategories: List<String>
)

/**
 * 把解析出的大纲区块写入某本书的作品大纲。
 * type → 分类标签（没有则新建）；name → 条目「显示标签」（同标签下重名时自动加序号）；
 * fields → 条目字段（key 为字段名，value 为内容）。
 */
class AiOutlineImporter(
    private val prefs: Prefs,
    private val repo: Repository
) {
    suspend fun import(bookId: Long, blocks: List<AiOutlineBlock>): AiOutlineImportResult {
        var categories = prefs.outlineCategories(bookId) ?: defaultOutlineKeys
        val newCategories = mutableListOf<String>()
        var items = 0
        // 同一次导入里后写的区块也要看到先写入的条目，所以每轮重新读取大纲
        blocks.forEach { block ->
            // 标签匹配：存储值或展示名（人设 / 世界观 / 故事大纲 / 灵感库）任一命中即归入该标签
            var categoryKey = categories.firstOrNull { key ->
                key == block.type || outlineCategoryLabel(key) == block.type
            }
            if (categoryKey == null) {
                categoryKey = block.type
                categories = categories + categoryKey
                prefs.setOutlineCategories(bookId, categories)
                newCategories += block.type
            }

            val existing = repo.getOutline(bookId).filter { it.category == categoryKey }
            val displayName = uniqueName(block.name, existing.map { displayNameOf(it) })
            val fields = listOf(OUTLINE_HEAD_LABEL to displayName) + block.fields
            repo.saveOutline(
                OutlineItem(
                    bookId = bookId,
                    category = categoryKey,
                    title = displayName,
                    content = encodeOutlineFields(fields),
                    sortOrder = existing.size
                )
            )
            items++

            // 把新字段名并进该标签的字段模板，之后手动新增条目时自动带上这些字段
            val template = prefs.outlineFields(bookId, categoryKey)
            val keys = block.fields.map { it.first }.filter { it != OUTLINE_HEAD_LABEL }
            when {
                template != null ->
                    prefs.setOutlineFields(bookId, categoryKey, (template + keys).distinct())
                block.type in newCategories ->
                    prefs.setOutlineFields(bookId, categoryKey, (listOf(OUTLINE_HEAD_LABEL) + keys).distinct())
            }
        }
        return AiOutlineImportResult(blocks = blocks.size, items = items, newCategories = newCategories)
    }

    /** 条目的对外显示名：首项「显示标签」的值，旧数据回落到标题 */
    private fun displayNameOf(item: OutlineItem): String =
        decodeOutlineFields(item.content).firstOrNull()?.second?.ifBlank { item.title } ?: item.title

    /** 同一标签下重名时按「名称（2）」「名称（3）」…挑一个不重复的 */
    private fun uniqueName(name: String, existing: List<String>): String {
        if (name !in existing) return name
        var i = 2
        while ("$name（$i）" in existing) i++
        return "$name（$i）"
    }
}
