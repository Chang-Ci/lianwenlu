package com.lianwenqi.app.data

import androidx.room.withTransaction
import com.lianwenqi.app.ai.AiClient
import com.lianwenqi.app.ai.AiRequest
import com.lianwenqi.app.ai.AiResult
import com.lianwenqi.app.ai.ModelEndpoint
import com.lianwenqi.app.data.entity.AiModel
import com.lianwenqi.app.data.entity.Book
import com.lianwenqi.app.data.entity.Chapter
import com.lianwenqi.app.data.entity.ChatMessage
import com.lianwenqi.app.data.entity.ChatRole
import com.lianwenqi.app.data.entity.ChatSession
import com.lianwenqi.app.data.entity.OutlineItem
import com.lianwenqi.app.data.entity.outlineCategoryLabel
import com.lianwenqi.app.data.entity.Prompt
import com.lianwenqi.app.data.entity.Provider
import com.lianwenqi.app.data.entity.Volume
import com.lianwenqi.app.util.TextTool
import com.lianwenqi.app.util.buildTitle
import com.lianwenqi.app.util.nextTitleIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SortMode { TIME, NAME }

/** 书籍列表所需的聚合信息 */
data class BookSummary(
    val book: Book,
    val wordCount: Int,
    val chapterCount: Int
)

class Repository(
    private val db: AppDatabase,
    private val ai: AiClient = AiClient()
) {
    private val bookDao = db.bookDao()
    private val volumeDao = db.volumeDao()
    private val chapterDao = db.chapterDao()
    private val outlineDao = db.outlineDao()
    private val promptDao = db.promptDao()
    private val providerDao = db.providerDao()
    private val modelDao = db.aiModelDao()
    private val chatDao = db.chatDao()
    private val groupDao = db.bookGroupDao()

    // ---------------- 书架 ----------------

    fun observeBooks(mode: SortMode, asc: Boolean): Flow<List<Book>> = when (mode) {
        SortMode.TIME -> if (asc) bookDao.observeByTimeAsc() else bookDao.observeByTimeDesc()
        SortMode.NAME -> if (asc) bookDao.observeByNameAsc() else bookDao.observeByNameDesc()
    }

    fun observeBookSummaries(mode: SortMode, asc: Boolean): Flow<List<BookSummary>> =
        observeBooks(mode, asc).map { books ->
            books.map { b ->
                val chapters = chapterDao.getByBook(b.id)
                BookSummary(
                    book = b,
                    wordCount = chapters.sumOf { TextTool.countWords(it.content) },
                    chapterCount = chapters.size
                )
            }
        }

    fun observeBook(id: Long): Flow<Book?> = bookDao.observeById(id)

    suspend fun getBook(id: Long): Book? = bookDao.getById(id)

    suspend fun allBooks(): List<Book> = bookDao.getAll()

    /** 新建书籍：同时创建首卷与首章 */
    suspend fun createBook(
        title: String,
        intro: String,
        coverPath: String?,
        coverColor: Int,
        volumePrefix: String,
        chapterPrefix: String
    ): Long = db.withTransaction {
        val bookId = bookDao.insert(
            Book(
                title = title,
                intro = intro,
                coverPath = coverPath,
                coverColor = coverColor,
                volumePrefix = volumePrefix,
                chapterPrefix = chapterPrefix
            )
        )
        val volumeId = volumeDao.insert(
            Volume(bookId = bookId, title = buildTitle(volumePrefix, 1), sortOrder = 0)
        )
        val chapterId = chapterDao.insert(
            Chapter(
                bookId = bookId,
                volumeId = volumeId,
                title = buildTitle(chapterPrefix, 1),
                sortOrder = 0
            )
        )
        bookDao.touch(bookId, System.currentTimeMillis(), chapterId, buildTitle(chapterPrefix, 1))
        bookId
    }

    suspend fun deleteBook(bookId: Long) = db.withTransaction {
        chapterDao.getByBook(bookId).forEach { chapterDao.deleteById(it.id) }
        volumeDao.getByBook(bookId).forEach { volumeDao.deleteById(it.id) }
        outlineDao.getByBook(bookId).forEach { outlineDao.deleteById(it.id) }
        bookDao.deleteById(bookId)
    }

    suspend fun setPinned(bookId: Long, pinned: Boolean) = bookDao.setPinned(bookId, pinned)

    suspend fun moveToGroup(bookId: Long, groupId: Long?) = bookDao.setGroup(bookId, groupId)

    suspend fun touchBook(bookId: Long, chapterId: Long?, chapterTitle: String?) =
        bookDao.touch(bookId, System.currentTimeMillis(), chapterId, chapterTitle)

    fun observeGroups() = groupDao.observeAll()
    suspend fun createGroup(name: String) = groupDao.insert(
        com.lianwenqi.app.data.entity.BookGroup(name = name)
    )
    suspend fun deleteGroup(id: Long) = groupDao.delete(
        com.lianwenqi.app.data.entity.BookGroup(id = id, name = "")
    )

    // ---------------- 分卷 / 章节 ----------------

    fun observeVolumes(bookId: Long): Flow<List<Volume>> = volumeDao.observeByBook(bookId)

    fun observeChapters(bookId: Long): Flow<List<Chapter>> = chapterDao.observeByBook(bookId)

    suspend fun getChapters(bookId: Long): List<Chapter> = chapterDao.getByBook(bookId)

    suspend fun getVolumes(bookId: Long): List<Volume> = volumeDao.getByBook(bookId)

    fun observeChapter(id: Long): Flow<Chapter?> = chapterDao.observeById(id)

    suspend fun getChapter(id: Long): Chapter? = chapterDao.getById(id)

    suspend fun createVolume(bookId: Long, prefix: String? = null): Long {
        val book = bookDao.getById(bookId)
        val volumes = volumeDao.getByBook(bookId)
        val template = prefix ?: book?.volumePrefix ?: "第%n卷"
        val index = nextTitleIndex(template, volumes.map { it.title })
        return volumeDao.insert(
            Volume(bookId = bookId, title = buildTitle(template, index), sortOrder = volumes.size)
        )
    }

    /** 新建章节。volumeId 为空时放入最后一卷 */
    suspend fun createChapter(bookId: Long, volumeId: Long? = null): Long {
        val book = bookDao.getById(bookId)
        val chapters = chapterDao.getByBook(bookId)
        val order = chapters.size
        val vid = volumeId ?: volumeDao.getByBook(bookId).lastOrNull()?.id
        val template = book?.chapterPrefix ?: "第%n章"
        // 编号按「当前分卷内」顺延：每个分卷都从第1章重新开始
        val siblings = chapters.filter { it.volumeId == vid }
        val title = buildTitle(template, nextTitleIndex(template, siblings.map { it.title }))
        val id = chapterDao.insert(
            Chapter(bookId = bookId, volumeId = vid, title = title, sortOrder = order)
        )
        touchBook(bookId, id, title)
        return id
    }

    suspend fun saveChapter(chapter: Chapter) = chapterDao.update(chapter.copy(updatedAt = System.currentTimeMillis()))

    suspend fun saveChapterContent(id: Long, title: String, summary: String, content: String, cursor: Int) {
        chapterDao.saveContent(id, title, summary, content, cursor, System.currentTimeMillis())
    }

    suspend fun deleteChapter(bookId: Long, chapterId: Long) {
        chapterDao.deleteById(chapterId)
        val remaining = chapterDao.getByBook(bookId)
        remaining.forEachIndexed { i, c -> if (c.sortOrder != i) chapterDao.setOrder(c.id, i) }
    }

    suspend fun deleteVolume(volumeId: Long) {
        volumeDao.deleteById(volumeId)
    }

    suspend fun renameVolume(volume: Volume, title: String) = volumeDao.update(volume.copy(title = title))

    /** 排序：按 sortOrder 列表重排章节 */
    suspend fun reorderChapters(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> chapterDao.setOrder(id, index) }
    }

    suspend fun reorderVolumes(ordered: List<Volume>) {
        ordered.forEachIndexed { index, v -> volumeDao.setOrder(v.id, index) }
    }

    suspend fun moveChapterToVolume(chapterId: Long, volumeId: Long?, order: Int) =
        chapterDao.moveToVolume(chapterId, volumeId, order)

    suspend fun searchInBook(bookId: Long, keyword: String): List<Chapter> =
        if (keyword.isBlank()) emptyList() else chapterDao.searchInBook(bookId, keyword)

    // ---------------- 大纲 ----------------

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

    // ---------------- 提示词 ----------------

    fun observePrompts(): Flow<List<Prompt>> = promptDao.observeAll()

    suspend fun getPrompts(): List<Prompt> = promptDao.getAll()

    suspend fun savePrompt(prompt: Prompt) {
        if (prompt.id == 0L) promptDao.insert(prompt) else promptDao.update(prompt)
    }

    suspend fun deletePrompt(id: Long) = promptDao.deleteById(id)

    // ---------------- 供应商 / 模型 ----------------

    fun observeProviders(): Flow<List<Provider>> = providerDao.observeAll()

    suspend fun getProviders(): List<Provider> = providerDao.getAll()

    suspend fun saveProvider(provider: Provider): Long =
        if (provider.id == 0L) providerDao.insert(provider)
        else {
            providerDao.update(provider); provider.id
        }

    suspend fun deleteProvider(id: Long) {
        modelDao.getByProvider(id).forEach { modelDao.deleteById(it.id) }
        providerDao.deleteById(id)
    }

    fun observeModels(): Flow<List<AiModel>> = modelDao.observeAll()

    suspend fun getModels(): List<AiModel> = modelDao.getAll()

    suspend fun getModel(id: Long): AiModel? = modelDao.getById(id)

    suspend fun saveModel(model: AiModel): Long {
        if (model.isDefault) modelDao.clearDefault()
        return if (model.id == 0L) modelDao.insert(model)
        else {
            modelDao.update(model); model.id
        }
    }

    suspend fun deleteModel(id: Long) = modelDao.deleteById(id)

    suspend fun fetchRemoteModels(provider: Provider): List<String> =
        ai.listModels(provider.baseUrl, provider.apiKey)

    /** 组装可用于发请求的模型配置 */
    suspend fun endpointFor(modelId: Long): ModelEndpoint? {
        val model = modelDao.getById(modelId) ?: return null
        val provider = providerDao.getById(model.providerId) ?: return null
        return ModelEndpoint(
            provider = provider,
            modelName = model.name,
            apiType = provider.apiType,
            temperature = model.temperature,
            maxTokens = model.maxTokens,
            maxContext = model.maxContext,
            systemPrompt = model.systemPrompt
        )
    }

    suspend fun sendToModel(
        modelId: Long,
        request: AiRequest,
        onDelta: ((String, String) -> Unit)? = null
    ): AiResult {
        val endpoint = endpointFor(modelId) ?: return AiResult.fail("未找到模型配置，请先在设置中添加")
        val merged = if (request.systemPrompt.isNotBlank() && endpoint.systemPrompt.isBlank()) {
            endpoint.copy(systemPrompt = request.systemPrompt)
        } else {
            endpoint
        }
        return ai.chat(merged, request.turns, onDelta)
    }

    // ---------------- 对话 ----------------

    fun observeSessions(): Flow<List<ChatSession>> = chatDao.observeSessions()

    suspend fun createSession(
        title: String,
        bookId: Long?,
        modelId: Long?,
        chapterId: Long? = null
    ): Long =
        chatDao.insertSession(
            ChatSession(title = title, bookId = bookId, chapterId = chapterId, modelId = modelId)
        )

    suspend fun getSession(id: Long): ChatSession? = chatDao.getSession(id)

    /** 章节内 AI 抽屉恢复历史对话 */
    suspend fun getSessionByChapter(chapterId: Long): ChatSession? = chatDao.getSessionByChapter(chapterId)

    /** 「跟书」模式：该书最近一次绑定本书的会话 */
    suspend fun getBookSession(bookId: Long): ChatSession? = chatDao.getBookSession(bookId)

    /** 「跟书」模式：该书全部绑定本书的会话，最新的在前 */
    suspend fun bookSessions(bookId: Long): List<ChatSession> = chatDao.bookSessions(bookId)

    /** 「跟章节」模式：该章节下的全部会话，最新的在前 */
    suspend fun sessionsByChapter(chapterId: Long): List<ChatSession> =
        chatDao.sessionsByChapter(chapterId)

    suspend fun updateSessions(sessions: List<ChatSession>) = chatDao.updateSessions(sessions)

    suspend fun updateSession(session: ChatSession) = chatDao.updateSession(session)

    /** 修改会话标题 */
    suspend fun updateSessionTitle(id: Long, title: String) = chatDao.updateSessionTitle(id, title)

    suspend fun deleteSession(id: Long) {
        chatDao.clearMessages(id)
        chatDao.deleteSession(id)
    }

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> = chatDao.observeMessages(sessionId)

    suspend fun getMessages(sessionId: Long): List<ChatMessage> = chatDao.getMessages(sessionId)

    suspend fun addMessage(
        sessionId: Long,
        role: ChatRole,
        content: String,
        replyToId: Long = 0,
        variantIndex: Int = 0,
        reasoning: String = "",
        contextSnapshot: String = "",
        promptExtra: String = "",
        promptTokens: Int = 0,
        completionTokens: Int = 0,
        cachedTokens: Int = 0
    ): Long {
        val id = chatDao.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = role,
                content = content,
                replyToId = replyToId,
                variantIndex = variantIndex,
                reasoning = reasoning,
                contextSnapshot = contextSnapshot,
                promptExtra = promptExtra,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                cachedTokens = cachedTokens
            )
        )
        chatDao.getSession(sessionId)?.let {
            chatDao.updateSession(it.copy(updatedAt = System.currentTimeMillis()))
        }
        return id
    }

    /** 编辑已有消息的正文 */
    suspend fun updateMessageContent(id: Long, content: String) = chatDao.updateContent(id, content)

    /** 设置某条回答正文的折叠状态（收起后再次进入会话仍保持） */
    suspend fun setContentCollapsed(id: Long, collapsed: Boolean) =
        chatDao.updateContentCollapsed(id, collapsed)

    suspend fun getMessage(id: Long): ChatMessage? = chatDao.getMessage(id)

    /** 批量删除指定消息（编辑历史提示词后丢弃旧内容用） */
    suspend fun deleteMessagesByIds(ids: List<Long>) {
        if (ids.isNotEmpty()) chatDao.deleteMessagesByIds(ids)
    }

    /** 下一个回答版本序号 */
    suspend fun nextVariantIndex(replyToId: Long): Int = chatDao.maxVariantIndex(replyToId) + 1

    /** 切换到某个回答版本 */
    suspend fun setActiveVariant(replyToId: Long, messageId: Long) {
        chatDao.deactivateVariants(replyToId)
        chatDao.activateMessage(messageId)
    }

    /** 取消某用户消息下所有回答版本的展示标记（用于插入新版本前） */
    suspend fun deactivateVariants(replyToId: Long) = chatDao.deactivateVariants(replyToId)

    /**
     * 删除消息：用户消息连同其所有回答版本一起删除；
     * 删除助手版本时，若删的是当前展示版本，则回退到剩余最后一个版本。
     */
    suspend fun deleteMessage(id: Long) {
        val msg = chatDao.getMessage(id) ?: return
        chatDao.deleteMessage(id)
        when {
            msg.role == ChatRole.USER -> chatDao.deleteVariants(id)
            msg.replyToId != 0L && msg.active -> {
                chatDao.getVariants(msg.replyToId).lastOrNull()?.let { chatDao.activateMessage(it.id) }
            }
        }
    }

    /** 按勾选的书籍 / 分卷 / 章节 / 人设 组装上下文 */
    suspend fun buildContext(
        bookIds: List<Long>,
        chapterIds: List<Long>,
        outlineIds: List<Long>,
        includeContent: Boolean,
        includeSummary: Boolean
    ): String {
        val sb = StringBuilder()
        bookIds.forEach { bid ->
            val book = bookDao.getById(bid) ?: return@forEach
            sb.append("《").append(book.title).append("》")
            if (book.intro.isNotBlank()) sb.append("（简介：").append(book.intro).append("）")
            sb.append('\n')
        }
        // 大纲：同一分类只输出一次分类标题，其下每条以「｛首项值｝」单独起行，
        // 其余字段逐行「字段名：值」，不再输出「显示标签：xxx」也不再重复分类标题
        val outlineItems = outlineIds.mapNotNull { outlineDao.getById(it) }
        if (outlineItems.isNotEmpty()) {
            val grouped = LinkedHashMap<String, MutableList<OutlineItem>>()
            outlineItems.forEach { grouped.getOrPut(it.category) { mutableListOf() }.add(it) }
            grouped.forEach { (category, items) ->
                items.forEachIndexed { index, item ->
                    val fields = decodeOutlineFields(item.content)
                    // 首项即条目的「显示标签」，其值作为对外标题；旧数据回落用 item.title
                    val head = fields.firstOrNull()?.second?.ifBlank { item.title } ?: item.title
                    if (index == 0) sb.append("【").append(outlineCategoryLabel(category)).append("】")
                    sb.append("｛").append(head).append("｝").append('\n')
                    fields.drop(1).forEach { f ->
                        sb.append(f.first).append("：").append(f.second).append('\n')
                    }
                }
            }
        }
        chapterIds.forEach { cid ->
            val ch = chapterDao.getById(cid) ?: return@forEach
            sb.append("【章节】").append(ch.title).append('\n')
            if (includeSummary && ch.summary.isNotBlank()) {
                sb.append("梗概：").append(ch.summary).append('\n')
            }
            if (includeContent) sb.append("正文：").append(ch.content).append('\n')
        }
        return sb.toString()
    }
}