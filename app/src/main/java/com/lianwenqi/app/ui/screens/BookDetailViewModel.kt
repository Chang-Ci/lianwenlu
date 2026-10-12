package com.lianwenqi.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianwenqi.app.AppContainer
import com.lianwenqi.app.data.entity.Book
import com.lianwenqi.app.data.entity.Chapter
import com.lianwenqi.app.data.entity.OutlineItem
import com.lianwenqi.app.data.entity.Volume
import com.lianwenqi.app.data.entity.defaultOutlineKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val container: AppContainer,
    private val bookId: Long
) : ViewModel() {

    private val repo = container.repository

    val book: StateFlow<Book?> =
        repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val volumes: StateFlow<List<Volume>> =
        repo.observeVolumes(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chapters: StateFlow<List<Chapter>> =
        repo.observeChapters(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outline: StateFlow<List<OutlineItem>> =
        repo.observeOutline(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createVolume() = viewModelScope.launch { repo.createVolume(bookId) }

    fun createChapter(volumeId: Long? = null, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = repo.createChapter(bookId, volumeId)
        onCreated(id)
    }

    fun deleteChapter(chapterId: Long) = viewModelScope.launch { repo.deleteChapter(bookId, chapterId) }

    fun deleteVolume(volumeId: Long) = viewModelScope.launch { repo.deleteVolume(volumeId) }

    fun renameVolume(volume: Volume, title: String) =
        viewModelScope.launch { repo.renameVolume(volume, title) }

    fun renameChapter(chapter: Chapter, title: String) = viewModelScope.launch {
        repo.saveChapter(chapter.copy(title = title))
    }

    fun saveOutline(item: OutlineItem) = viewModelScope.launch { repo.saveOutline(item) }

    /** 大纲字段表：未保存过时返回 null，由界面套用默认字段 */
    suspend fun outlineFields(category: String): List<String>? =
        container.prefs.outlineFields(bookId, category)

    fun saveOutlineFields(category: String, names: List<String>) = viewModelScope.launch {
        container.prefs.setOutlineFields(bookId, category, names)
    }

    fun deleteOutline(id: Long) = viewModelScope.launch { repo.deleteOutline(id) }

    // ---------------- 大纲分类标签 ----------------

    /** 当前书籍的大纲分类标签（存储值有序列表） */
    private val _categories = MutableStateFlow(defaultOutlineKeys)
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    init {
        viewModelScope.launch { refreshCategories() }
    }

    private suspend fun refreshCategories() {
        _categories.value = container.prefs.outlineCategories(bookId) ?: defaultOutlineKeys
    }

    fun addCategory(label: String) = viewModelScope.launch {
        val name = label.trim()
        if (name.isEmpty()) return@launch
        val current = container.prefs.outlineCategories(bookId) ?: defaultOutlineKeys
        if (name in current) return@launch
        container.prefs.setOutlineCategories(bookId, current + name)
        refreshCategories()
    }

    /** 删除一个分类标签，并移除该标签下的全部条目 */
    fun deleteCategory(key: String) = viewModelScope.launch {
        val current = container.prefs.outlineCategories(bookId) ?: defaultOutlineKeys
        container.prefs.setOutlineCategories(bookId, current - key)
        repo.deleteOutlineByCategory(bookId, key)
        refreshCategories()
    }

    /** 重命名一个分类标签，同步迁移该标签下的条目并保留字段模板 */
    fun renameCategory(oldKey: String, newLabel: String) = viewModelScope.launch {
        val name = newLabel.trim()
        val current = container.prefs.outlineCategories(bookId) ?: defaultOutlineKeys
        if (name.isEmpty() || name == oldKey || name in current) return@launch
        container.prefs.setOutlineCategories(bookId, current.map { if (it == oldKey) name else it })
        // 字段模板跟随新分类名，避免重命名后新增条目回落到默认字段
        val fields = container.prefs.outlineFields(bookId, oldKey) ?: defaultOutlineFields(oldKey)
        container.prefs.setOutlineFields(bookId, name, fields)
        repo.renameOutlineCategory(bookId, oldKey, name)
        refreshCategories()
    }

    /** 排序：按传入的 id 顺序重排分卷 */
    fun reorderVolumeIds(ids: List<Long>) = viewModelScope.launch {
        val byId = volumes.value.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        if (ordered.size >= 2) repo.reorderVolumes(ordered)
    }

    /** 排序：按传入的 id 顺序重排章节 */
    fun reorderChapterIds(ids: List<Long>) = viewModelScope.launch {
        if (ids.size >= 2) repo.reorderChapters(ids)
    }
}