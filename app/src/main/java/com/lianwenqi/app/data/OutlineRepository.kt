package com.lianwenqi.app.data

import com.lianwenqi.app.data.entity.OutlineItem
import kotlinx.coroutines.flow.Flow

/** 大纲领域：大纲条目（人设 / 世界观等分类标签下的内容） */
class OutlineRepository(private val db: AppDatabase) {
    private val outlineDao = db.outlineDao()

    fun observeOutline(bookId: Long): Flow<List<OutlineItem>> = outlineDao.observeByBook(bookId)

    suspend fun getOutline(bookId: Long): List<OutlineItem> = outlineDao.getByBook(bookId)

    suspend fun saveOutline(item: OutlineItem) {
        if (item.id == 0L) {
            outlineDao.insert(item)
        } else {
            outlineDao.update(item.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteOutline(id: Long) = outlineDao.deleteById(id)

    /** 删除某个分类标签下的全部条目 */
    suspend fun deleteOutlineByCategory(bookId: Long, category: String) =
        outlineDao.deleteByCategory(bookId, category)

    /** 重命名某个分类标签，并同步迁移该标签下的全部条目 */
    suspend fun renameOutlineCategory(bookId: Long, from: String, to: String) =
        outlineDao.renameCategory(bookId, from, to)
}
