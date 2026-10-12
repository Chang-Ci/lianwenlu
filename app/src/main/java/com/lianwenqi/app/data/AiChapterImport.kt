package com.lianwenqi.app.data

import com.lianwenqi.app.util.AiChapterBlock

/** 一次章节梗概导入的结果统计 */
data class AiChapterImportResult(
    /** 新建章节数 */
    val created: Int,
    /** 已有同名章节、覆盖梗概的数量 */
    val updated: Int,
    /** 本次新建的分卷名 */
    val newVolumes: List<String>
)

/**
 * 把解析出的章节梗概区块写入某本书的分卷 / 章节。
 * volume 为空 → 统一塞入第一卷（没有分卷则先创建首卷）；
 * volume 有值但不存在 → 创建同名分卷；
 * chapterName 在该卷已存在 → 覆盖该章梗概，否则新建章节。
 * 新章节按区块出现的先后顺序追加到对应卷末尾。
 */
class AiChapterImporter(private val repo: Repository) {

    suspend fun import(bookId: Long, blocks: List<AiChapterBlock>): AiChapterImportResult {
        var created = 0
        var updated = 0
        val newVolumes = mutableListOf<String>()

        var volumes = repo.getVolumes(bookId)
        if (volumes.isEmpty()) {
            // 没有分卷时先创建首卷，空 volume 的章节统一塞入第一卷
            repo.createVolume(bookId)
            volumes = repo.getVolumes(bookId)
            volumes.firstOrNull()?.let { newVolumes += it.title }
        }
        val firstVolumeId = volumes.first().id
        // 本次导入新建的分卷：卷名 → id，避免同卷名重复创建
        val createdVolumeIds = mutableMapOf<String, Long>()

        blocks.forEach { block ->
            if (block.chapterName.isBlank()) return@forEach
            val volumeId: Long = when {
                block.volume.isBlank() -> firstVolumeId
                else -> {
                    val existing = repo.getVolumes(bookId).firstOrNull { it.title == block.volume }?.id
                        ?: createdVolumeIds[block.volume]
                    if (existing != null) {
                        existing
                    } else {
                        val id = repo.createVolumeWithTitle(bookId, block.volume)
                        createdVolumeIds[block.volume] = id
                        newVolumes += block.volume
                        id
                    }
                }
            }
            // 该卷已有同名章节则覆盖梗概，否则新建章节
            val exist = repo.getChapters(bookId)
                .firstOrNull { it.volumeId == volumeId && it.title == block.chapterName }
            if (exist != null) {
                repo.saveChapter(exist.copy(summary = block.summary))
                updated++
            } else {
                repo.createChapterWithSummary(bookId, volumeId, block.chapterName, block.summary)
                created++
            }
        }
        return AiChapterImportResult(created = created, updated = updated, newVolumes = newVolumes)
    }
}
