package com.lianwenqi.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianwenqi.app.AppContainer
import com.lianwenqi.app.data.AiChapterImporter
import com.lianwenqi.app.data.AiOutlineImporter
import com.lianwenqi.app.data.ChatEvent
import com.lianwenqi.app.data.SendChatMessageUseCase
import com.lianwenqi.app.data.SendChatRequest
import com.lianwenqi.app.data.entity.AiModel
import com.lianwenqi.app.data.entity.Book
import com.lianwenqi.app.data.entity.BookGroup
import com.lianwenqi.app.data.entity.Chapter
import com.lianwenqi.app.data.entity.ChatMessage
import com.lianwenqi.app.data.entity.ChatRole
import com.lianwenqi.app.data.entity.ChatSession
import com.lianwenqi.app.data.entity.ContextSnapshot
import com.lianwenqi.app.data.entity.OutlineItem
import com.lianwenqi.app.data.entity.decodeContextSnapshot
import com.lianwenqi.app.data.entity.outlineCategoryLabel
import com.lianwenqi.app.data.entity.Prompt
import com.lianwenqi.app.data.entity.Provider
import com.lianwenqi.app.data.entity.Volume
import com.lianwenqi.app.util.AI_TOOL_IMPORT_CHAPTER
import com.lianwenqi.app.util.AI_TOOL_WRITE_OUTLINE
import com.lianwenqi.app.util.AiChapterBlock
import com.lianwenqi.app.util.AiOutlineBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val container: AppContainer,
    private val defaultBookId: Long? = null,
    private val fixedChapterId: Long? = null
) : ViewModel() {

    private val bookRepository = container.bookRepository
    private val chatRepository = container.chatRepository
    private val outlineRepository = container.outlineRepository
    private val configRepository = container.configRepository
    private val prefs = container.prefs

    /** 「发送消息」用例：组装上下文 → 流式请求 → 写回助手消息 → 解析工具区块 */
    private val sendChatMessage = SendChatMessageUseCase(chatRepository, configRepository)

    val prompts: StateFlow<List<Prompt>> =
        configRepository.observePrompts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val models: StateFlow<List<AiModel>> =
        configRepository.observeModels().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val providers: StateFlow<List<Provider>> =
        configRepository.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<Book>> =
        bookRepository.observeBooks(com.lianwenqi.app.data.SortMode.TIME, false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 书籍分组（选书弹窗的分组筛选用） */
    val groups: StateFlow<List<BookGroup>> =
        bookRepository.observeGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<ChatSession>> =
        chatRepository.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sessionId = MutableStateFlow<Long?>(null)
    val sessionId: StateFlow<Long?> = _sessionId.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = _sessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else chatRepository.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeModelId: StateFlow<Long> = prefs.activeModelId
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _outline = MutableStateFlow<List<OutlineItem>>(emptyList())
    val outline: StateFlow<List<OutlineItem>> = _outline.asStateFlow()

    private val _volumes = MutableStateFlow<List<Volume>>(emptyList())
    val volumes: StateFlow<List<Volume>> = _volumes.asStateFlow()

    /** 会话列表筛选：所选书籍的分卷 / 章节 */
    private val _filterVolumes = MutableStateFlow<List<Volume>>(emptyList())
    val filterVolumes: StateFlow<List<Volume>> = _filterVolumes.asStateFlow()
    private val _filterChapters = MutableStateFlow<List<Chapter>>(emptyList())
    val filterChapters: StateFlow<List<Chapter>> = _filterChapters.asStateFlow()

    val selectedBookIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedChapterIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedOutlineIds = MutableStateFlow<Set<Long>>(emptySet())
    /**
     * 章节上下文是否处于「全选」状态。
     * 编辑器场景下当前打开的章节不进上下文，所以「全选」的具体 id 集合会随章节切换而变化；
     * 记录该标记后，切到别的章节时仍是「全选」而不是被还原成上一章的具体列表。
     */
    val chapterAllSelected = MutableStateFlow(false)
    val includeContent = MutableStateFlow(true)
    val includeSummary = MutableStateFlow(true)

    /** 「＋本章梗概」：是否把当前打开章节的梗概额外加入上下文 */
    val currentSummaryOn = MutableStateFlow(false)
    val sending = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)
    val input = MutableStateFlow("")

    // ---------------- AI 工具 ----------------

    private val outlineImporter = AiOutlineImporter(prefs, outlineRepository)
    private val chapterImporter = AiChapterImporter(bookRepository)

    /** 「写入大纲工具」是否启用（设置-AI工具页控制；关闭后不在聊天页多选中出现） */
    val outlineToolEnabled: StateFlow<Boolean> = prefs.aiToolOutlineEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 「章节梗概导入工具」是否启用（设置-AI工具页控制；关闭后不在聊天页多选中出现） */
    val chapterToolEnabled: StateFlow<Boolean> = prefs.aiToolChapterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 聊天页勾选的 AI 工具 id 集合（可多选，持久化） */
    val chatTools: StateFlow<Set<String>> = prefs.aiChatTools
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * AI 回答完成后解析出的待导入内容队列。
     * 多选多个工具且回答里含多种标记块时逐个入队，UI 挨个询问是否导入。
     */
    val pendingImports = MutableStateFlow<List<PendingImport>>(emptyList())

    /** 导入结果提示（Toast 用，展示后由 UI 清空） */
    val importToast = MutableStateFlow<String?>(null)

    fun toggleChatTool(id: String) = viewModelScope.launch {
        val cur = chatTools.value
        prefs.setAiChatTools(if (id in cur) cur - id else cur + id)
    }

    /** 「写入大纲工具」当前是否生效：工具启用且已在聊天页勾选 */
    private fun writeOutlineToolOn(): Boolean =
        outlineToolEnabled.value && AI_TOOL_WRITE_OUTLINE in chatTools.value

    /** 「章节梗概导入工具」当前是否生效：工具启用且已在聊天页勾选 */
    private fun chapterImportToolOn(): Boolean =
        chapterToolEnabled.value && AI_TOOL_IMPORT_CHAPTER in chatTools.value

    /** 取消当前这一项导入（队列后移，继续询问下一项） */
    fun dismissPendingImport() {
        pendingImports.value = pendingImports.value.drop(1)
    }

    fun clearImportToast() {
        importToast.value = null
    }

    /** 导入的默认目标书籍：唯一勾选的书 > 会话绑定的书 */
    suspend fun importTargetBookId(): Long? =
        selectedBookIds.value.singleOrNull()
            ?: _sessionId.value?.let { chatRepository.getSession(it)?.bookId }

    /** 把待导入的大纲区块写入指定书籍，然后继续队列中的下一项 */
    fun confirmOutlineImport(bookId: Long) = viewModelScope.launch {
        val head = pendingImports.value.firstOrNull() as? PendingImport.Outline ?: return@launch
        pendingImports.value = pendingImports.value.drop(1)
        val result = outlineImporter.import(bookId, head.blocks)
        importToast.value = buildString {
            append("已写入 ${result.items} 条大纲")
            if (result.newCategories.isNotEmpty()) {
                append("，新建标签：${result.newCategories.joinToString("、")}")
            }
        }
        // 刷新上下文里的大纲列表，让「大纲(n)」立即反映新条目
        loadBookContextNow(selectedBookIds.value.firstOrNull() ?: bookId)
    }

    /** 把待导入的章节梗概写入指定书籍，然后继续队列中的下一项 */
    fun confirmChapterImport(bookId: Long) = viewModelScope.launch {
        val head = pendingImports.value.firstOrNull() as? PendingImport.Chapters ?: return@launch
        pendingImports.value = pendingImports.value.drop(1)
        val result = chapterImporter.import(bookId, head.blocks)
        importToast.value = buildString {
            append("已导入章节梗概：新建 ${result.created} 章")
            if (result.updated > 0) append("，更新 ${result.updated} 章")
            if (result.newVolumes.isNotEmpty()) {
                append("，新建分卷：${result.newVolumes.joinToString("、")}")
            }
        }
        // 刷新上下文里的章节 / 分卷列表，让「章节(n)」立即反映新章节
        loadBookContextNow(selectedBookIds.value.firstOrNull() ?: bookId)
    }

    /** 正在编辑的历史用户消息 id（0 表示普通发送，>0 表示发送后将改写该条并重新生成） */
    val editingUserMessageId = MutableStateFlow(0L)

    /** 正在生成回答的用户消息 id：UI 据此把该条回答位置就地切成“正在生成” */
    val pendingReplyToId = MutableStateFlow(0L)

    /** 正在生成的回答正文（流式增量累积），用于实时显示 */
    val streamingContent = MutableStateFlow("")

    /** 正在生成的深度思考内容（流式增量累积） */
    val streamingReasoning = MutableStateFlow("")

    /** 在途生成任务，供「停止响应」取消 */
    private var generateJob: Job? = null

    init {
        if (defaultBookId != null) selectedBookIds.value = setOf(defaultBookId)
        viewModelScope.launch {
            // 编辑器场景下当前打开的章节不进入章节上下文，由「＋本章梗概」单独加入
            loadBookContextNow(selectedBookIds.value.firstOrNull() ?: defaultBookId)
            // 从章节内进入：按会话归属恢复 / 建立会话，并套用该书上次的上下文选择
            restoreInitialSession()
            watchContextChanges()
        }
        // AI 聊天页切换书籍：重新加载该书上下文并套用其上次的选择
        viewModelScope.launch {
            selectedBookIds.drop(1).collect { ids ->
                loadBookContextNow(ids.firstOrNull())
                restoreContextMemory()
            }
        }
    }

    fun loadBookContext(bookId: Long? = null) {
        viewModelScope.launch { loadBookContextNow(bookId) }
    }

    private suspend fun loadBookContextNow(bookId: Long? = null) {
        val ids = selectedBookIds.value.toList().ifEmpty { listOfNotNull(bookId) }
        if (ids.isEmpty()) {
            _chapters.value = emptyList()
            _outline.value = emptyList()
            _volumes.value = emptyList()
        } else {
            _chapters.value = ids.flatMap { bookRepository.getChapters(it) }
            _outline.value = ids.flatMap { outlineRepository.getOutline(it) }
            _volumes.value = ids.flatMap { bookRepository.getVolumes(it) }
        }
    }

    /** 会话列表筛选：按书籍加载分卷 / 章节，用于「某卷 / 某章」筛选下拉 */
    fun loadFilterContext(bookId: Long?) {
        viewModelScope.launch {
            if (bookId == null) {
                _filterVolumes.value = emptyList()
                _filterChapters.value = emptyList()
            } else {
                _filterVolumes.value = bookRepository.getVolumes(bookId)
                _filterChapters.value = bookRepository.getChapters(bookId)
            }
        }
    }

    /** 当前上下文记忆归属的书籍：仅单选一本书时有效 */
    private fun memoryBookId(): Long? =
        selectedBookIds.value.singleOrNull() ?: defaultBookId

    /**
     * 进入 AI 面板时决定展示哪个会话：
     * 跟章节 → 该章节上一次的会话；跟书 → 该书最近一次绑定本书的会话（没有则新建）。
     */
    private suspend fun restoreInitialSession() {
        val chapterId = fixedChapterId
        if (chapterId != null) {
            if (prefs.aiFollowBook.first()) {
                val bookId = memoryBookId()
                if (bookId != null) {
                    val existing = chatRepository.getBookSession(bookId)
                    if (existing != null) {
                        _sessionId.value = existing.id
                        restoreContextMemory()
                    } else {
                        _sessionId.value = createBookSession(bookId, activeModelId.value.takeIf { it > 0 })
                        applyNewBookSessionDefaults(bookId)
                    }
                    return
                }
            } else {
                chatRepository.getSessionByChapter(chapterId)?.let { _sessionId.value = it.id }
            }
        }
        restoreContextMemory()
    }

    /** 套用「本书上次选中的章节 / 大纲与梗概正文开关」 */
    private suspend fun restoreContextMemory() {
        val bookId = memoryBookId() ?: return
        val mem = prefs.aiContextMemory(bookId) ?: return
        val chapterIds = _chapters.value.map { it.id }.toSet()
        val outlineIds = _outline.value.map { it.id }.toSet()
        if (mem.chapterAll) {
            // 上次是「全选」：按当前章节重新展开，切章节后依然保持全选
            selectedChapterIds.value = contextChapterIds()
            chapterAllSelected.value = true
        } else {
            selectedChapterIds.value = mem.chapterIds.filter { it in chapterIds }.toSet()
            refreshChapterAllFlag()
        }
        selectedOutlineIds.value = mem.outlineIds.filter { it in outlineIds }.toSet()
        includeSummary.value = mem.includeSummary
        includeContent.value = mem.includeContent
    }

    /** 当前章节上下文的「全部章节」：编辑器场景排除当前打开的章节 */
    private fun contextChapterIds(): Set<Long> =
        _chapters.value.filter { it.id != fixedChapterId }.map { it.id }.toSet()

    /** 依据当前选中集合重新判定是否仍处于「全选」 */
    private fun refreshChapterAllFlag() {
        val all = contextChapterIds()
        chapterAllSelected.value = all.isNotEmpty() && selectedChapterIds.value == all
    }

    /** 上下文选择变化时写入「本书记忆」，下次进入同一本书自动套用 */
    private suspend fun watchContextChanges() {
        combine(
            selectedChapterIds,
            selectedOutlineIds,
            includeSummary,
            includeContent,
            chapterAllSelected
        ) { _, _, _, _, _ -> Unit }.collect {
            val bookId = memoryBookId() ?: return@collect
            prefs.setAiContextMemory(
                bookId,
                ContextSnapshot(
                    bookIds = selectedBookIds.value.toList(),
                    chapterIds = selectedChapterIds.value.toList(),
                    outlineIds = selectedOutlineIds.value.toList(),
                    includeSummary = includeSummary.value,
                    includeContent = includeContent.value,
                    chapterAll = chapterAllSelected.value
                )
            )
        }
    }

    /** 单选一本书（选书弹窗改为单选）；再次传同一本时取消选择 */
    fun selectBook(id: Long?) {
        selectedBookIds.value = if (id == null) emptySet() else setOf(id)
        loadBookContext()
    }

    /** 快速新建书籍（默认前缀），返回新书 id */
    suspend fun createBook(title: String): Long = bookRepository.createBook(
        title = title.trim(),
        intro = "",
        coverPath = null,
        coverColor = 0xFF3F51B5.toInt(),
        volumePrefix = "第%n卷",
        chapterPrefix = "第%n章"
    )

    /** 完整参数新建书籍（新建书籍表单提交用），返回新书 id */
    suspend fun createBookFull(
        title: String,
        intro: String,
        coverPath: String?,
        coverColor: Int,
        volumePrefix: String,
        chapterPrefix: String,
        groupId: Long?
    ): Long = bookRepository.createBook(title, intro, coverPath, coverColor, volumePrefix, chapterPrefix, groupId)

    /** 复制封面图到应用私有目录，返回本地路径 */
    suspend fun pickCover(uri: android.net.Uri): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.lianwenqi.app.util.FileUtil.copyToInternal(container.context, uri)
        }

    /** 新建分组并返回新分组 id（书籍表单内现场建组用） */
    suspend fun createGroupAndGet(name: String): Long = bookRepository.createGroup(name)

    /** 快速新建书籍并选为当前上下文书籍 */
    fun createBookAndSelect(title: String) = viewModelScope.launch {
        selectBook(createBook(title))
    }

    fun toggleChapter(id: Long) {
        selectedChapterIds.value = selectedChapterIds.value.toggle(id)
        refreshChapterAllFlag()
    }

    fun toggleOutline(id: Long) {
        selectedOutlineIds.value = selectedOutlineIds.value.toggle(id)
    }

    fun selectAllChapters() {
        val all = contextChapterIds()
        selectedChapterIds.value =
            if (selectedChapterIds.value.containsAll(all) && all.isNotEmpty()) emptySet() else all
        refreshChapterAllFlag()
    }

    fun selectAllOutline() {
        val all = _outline.value.map { it.id }.toSet()
        selectedOutlineIds.value =
            if (selectedOutlineIds.value.containsAll(all) && all.isNotEmpty()) emptySet() else all
    }

    /** 大纲中某个分类（人设 / 世界观 / 故事大纲 / 灵感库 / 自定义标签）整组勾选 */
    fun toggleOutlineCategory(category: String) {
        val all = _outline.value.filter { it.category == category }.map { it.id }
        if (all.isEmpty()) return
        selectedOutlineIds.value =
            if (all.all { it in selectedOutlineIds.value }) selectedOutlineIds.value - all.toSet()
            else selectedOutlineIds.value + all.toSet()
    }

    /** 「＋本章梗概」：把当前打开章节的梗概加入 / 移出上下文 */
    fun toggleCurrentSummary() {
        currentSummaryOn.value = !currentSummaryOn.value
    }

    fun appendToInput(text: String) {
        if (text.isBlank()) return
        input.value = if (input.value.isBlank()) text else input.value.trimEnd() + "\n" + text
    }

    /** 一键把作品大纲（人设/故事线/灵感等）加入输入框 */
    fun appendOutline() {
        val text = buildString {
            append("【作品大纲】\n")
            _outline.value.forEach {
                if (it.tag.isNotBlank()) append("[${it.tag}] ")
                append(outlineCategoryLabel(it.category)).append(" - ").append(it.title)
                if (it.content.isNotBlank()) append("：").append(it.content)
                append('\n')
            }
        }
        appendToInput(text.trim())
    }

    /** 一键把章节梗概加入输入框 */
    fun appendSummaries() {
        val text = buildString {
            append("【章节梗概】\n")
            _chapters.value.filter { selectedChapterIds.value.isEmpty() || it.id in selectedChapterIds.value }
                .forEach { append(it.title).append("：").append(it.summary.ifBlank { "（暂无梗概）" }).append('\n') }
        }
        appendToInput(text.trim())
    }

    fun appendPrompt(prompt: Prompt) = appendToInput(prompt.content)

    fun setModel(id: Long) = viewModelScope.launch { prefs.setActiveModelId(id) }

    fun ensureSession(): Long? {
        val existing = _sessionId.value
        if (existing != null) return existing
        return null
    }

    /**
     * 「＋」新开对话：
     * 跟章节 → 会话命名「书名-卷名-章节名」，并销毁该章节此前的对话；
     * 跟书 → 新建绑定本书的对话（标题按「书名（现）/（1）…」重排），默认全选章节 + 仅梗概。
     */
    fun newSession() {
        viewModelScope.launch {
            val bookId = selectedBookIds.value.firstOrNull() ?: defaultBookId
            val chapterId = fixedChapterId
            val modelId = activeModelId.value.takeIf { it > 0 }
            when {
                chapterId != null && !prefs.aiFollowBook.first() -> {
                    _sessionId.value = createChapterSession(chapterId, bookId, modelId)
                    restoreContextMemory()
                }
                chapterId != null && bookId != null -> {
                    _sessionId.value = createBookSession(bookId, modelId)
                    applyNewBookSessionDefaults(bookId)
                }
                else -> {
                    _sessionId.value = chatRepository.createSession("新对话", bookId, modelId, null)
                }
            }
            editingUserMessageId.value = 0L
            currentSummaryOn.value = false
        }
    }

    /** 按当前模式（跟书 / 跟章节）为新会话建立归属，供发送时兜底建会话使用 */
    private suspend fun createSessionForCurrentMode(modelId: Long): Long {
        val bookId = selectedBookIds.value.firstOrNull() ?: defaultBookId
        val chapterId = fixedChapterId
        return when {
            chapterId != null && !prefs.aiFollowBook.first() ->
                createChapterSession(chapterId, bookId, modelId)
            chapterId != null && bookId != null ->
                createBookSession(bookId, modelId)
            else ->
                chatRepository.createSession("新对话", bookId, modelId, null)
        }
    }

    /** 新建绑定本书的会话，并按「书名（现）/（1）/（2）…」重排该书会话标题 */
    private suspend fun createBookSession(bookId: Long, modelId: Long?): Long {
        val id = chatRepository.createSession("新对话", bookId, modelId, null)
        val bookTitle = bookRepository.getBook(bookId)?.title.orEmpty().ifBlank { "本书" }
        chatRepository.updateSessions(
            chatRepository.bookSessions(bookId).mapIndexed { index, s ->
                s.copy(title = if (index == 0) "$bookTitle（现）" else "$bookTitle（$index）")
            }
        )
        return id
    }

    /** 跟书模式新建对话后的默认上下文：全选章节 + 仅梗概；大纲按「首轮全选」开关处理 */
    private suspend fun applyNewBookSessionDefaults(bookId: Long) {
        includeSummary.value = true
        includeContent.value = false
        loadBookContextNow(bookId)
        selectedChapterIds.value = contextChapterIds()
        chapterAllSelected.value = true
        if (prefs.aiOutlineFirstRound.first()) {
            // 「首轮全选大纲」：把全部大纲写进首轮前缀，后续轮次靠前缀复用不再改写
            selectedOutlineIds.value = _outline.value.map { it.id }.toSet()
        } else {
            val remembered = prefs.aiContextMemory(bookId)?.outlineIds.orEmpty().toSet()
            selectedOutlineIds.value = _outline.value.filter { it.id in remembered }.map { it.id }.toSet()
        }
    }

    /** 跟章节模式：同一章节只保留最新一次对话，会话标题为「书名-卷名-章节名」 */
    private suspend fun createChapterSession(chapterId: Long, bookId: Long?, modelId: Long?): Long {
        val id = chatRepository.createSession(chapterSessionTitle(chapterId), bookId, modelId, chapterId)
        chatRepository.sessionsByChapter(chapterId).filter { it.id != id }.forEach { chatRepository.deleteSession(it.id) }
        return id
    }

    /** 跟章节模式的会话标题：书名-卷名-章节名 */
    private suspend fun chapterSessionTitle(chapterId: Long): String {
        val chapter = bookRepository.getChapter(chapterId) ?: return "新对话"
        val bookTitle = bookRepository.getBook(chapter.bookId)?.title.orEmpty()
        val volumeTitle = chapter.volumeId?.let { vid ->
            bookRepository.getVolumes(chapter.bookId).firstOrNull { it.id == vid }?.title.orEmpty()
        }.orEmpty()
        return listOf(bookTitle, volumeTitle, chapter.title)
            .filter { it.isNotBlank() }
            .joinToString("-")
            .ifBlank { "新对话" }
    }

    fun openSession(id: Long) {
        _sessionId.value = id
        viewModelScope.launch {
            chatRepository.getSession(id)?.bookId?.let { loadBookContext(it) }
        }
    }

    fun deleteSession(id: Long) = viewModelScope.launch {
        chatRepository.deleteSession(id)
        if (_sessionId.value == id) _sessionId.value = null
    }

    /** 修改会话标题（长按会话触发） */
    fun renameSession(id: Long, title: String) = viewModelScope.launch {
        val t = title.trim()
        if (t.isNotEmpty()) chatRepository.updateSessionTitle(id, t)
    }

    /** 启动一次生成任务并记录 Job，便于「停止响应」随时取消 */
    private fun launchGenerating(block: suspend CoroutineScope.() -> Unit) {
        // 已有生成在途时忽略新请求，避免两个任务互相覆盖生成态
        if (generateJob?.isActive == true || sending.value) return
        generateJob = viewModelScope.launch(block = block)
    }

    /** 停止在途生成：取消协程，进而中断正在等待的 HTTP 请求 */
    fun stopGenerating() {
        val job = generateJob
        generateJob = null
        job?.cancel()
        // 立即清除生成态，避免被取消的旧任务回调稍后才到、导致界面卡在「正在生成」
        sending.value = false
        pendingReplyToId.value = 0L
        streamingContent.value = ""
        streamingReasoning.value = ""
    }

    fun send(overrideText: String? = null, systemPrompt: String = "") = launchGenerating {
        val text = (overrideText ?: input.value).trim()
        if (text.isBlank() || sending.value) return@launchGenerating
        val modelId = activeModelId.value
        if (modelId <= 0L) {
            errorMessage.value = "请先在设置中配置并选择一个模型"
            return@launchGenerating
        }
        val sid = _sessionId.value
            ?: createSessionForCurrentMode(modelId).also { _sessionId.value = it }

        // 编辑历史提示词后发送：改写该条消息，并追加一个新的回答版本
        // （旧回答保留为历史版本，可翻页回看；只丢弃这条之后的后续对话）
        val editingId = editingUserMessageId.value
        if (editingId > 0L) {
            editingUserMessageId.value = 0L
            val target = chatRepository.getMessage(editingId)
            if (target != null && target.sessionId == sid) {
                val next = chatRepository.nextVariantIndex(editingId)
                val staleIds = chatRepository.getMessages(sid)
                    .filter {
                        it.id > editingId &&
                            !(it.role == ChatRole.ASSISTANT && it.replyToId == editingId)
                    }
                    .map { it.id }
                chatRepository.updateMessageContent(editingId, text)
                if (overrideText == null) input.value = ""
                val ok = runGeneration(
                    sid, modelId, systemPrompt,
                    replyToUserId = editingId, historyEndId = editingId, variantIndex = next
                )
                if (ok) chatRepository.deleteMessagesByIds(staleIds)
                return@launchGenerating
            }
        }

        // 「首轮全选大纲」：本次是会话第一条用户消息时，先把大纲写进快照，随后清空勾选。
        // 后续轮次 UI 默认不勾选大纲，靠前缀复用保持稳定；用户重新勾选才改写前缀。
        val isFirstUserMessage = chatRepository.getMessages(sid).none { it.role == ChatRole.USER }
        // 「＋本章梗概」：章节名与梗概作为附加提示词随消息落库（气泡里不显示），
        // 成为历史对话的一部分（不进最前面的参考资料前缀块）。
        // 下一轮从数据库重建历史时与上一轮实际发送的内容逐字节一致 → 前缀不被改写 → 缓存命中稳定。
        val userId = chatRepository.addMessage(
            sid, ChatRole.USER, text,
            contextSnapshot = currentSnapshot().encode(),
            promptExtra = chapterBlockSuffix()
        )
        if (overrideText == null) input.value = ""
        // 「首轮全选」：首轮把「全选大纲 + 全选章节（仅梗概/仅正文按当时勾选）」写进最前面的前缀，
        // 随后取消这两项勾选；后续轮次靠前缀复用保持稳定，只有重新勾选才改写前缀。
        if (isFirstUserMessage && firstRoundPrefixEnabled()) {
            selectedOutlineIds.value = emptySet()
            selectedChapterIds.value = emptySet()
            chapterAllSelected.value = false
        }
        runGeneration(sid, modelId, systemPrompt, replyToUserId = userId, historyEndId = userId, variantIndex = 0)
    }

    /** 当前勾选的上下文（书籍/章节/大纲 + 是否含梗概/正文） */
    fun currentSnapshot(): ContextSnapshot = ContextSnapshot(
        bookIds = selectedBookIds.value.toList(),
        chapterIds = selectedChapterIds.value.toList(),
        outlineIds = selectedOutlineIds.value.toList(),
        includeSummary = includeSummary.value,
        includeContent = includeContent.value,
        chapterAll = chapterAllSelected.value
    )

    /** 点击编辑某条已发送的消息：恢复它的正文与当时选好的上下文 */
    fun beginEditUserMessage(message: ChatMessage) {
        input.value = message.content
        editingUserMessageId.value = message.id
        val snapshot = decodeContextSnapshot(message.contextSnapshot)
        if (!snapshot.isEmpty) restoreContext(snapshot)
    }

    /** 取消编辑历史提示词，回到普通发送 */
    fun cancelEditUserMessage() {
        editingUserMessageId.value = 0L
        input.value = ""
    }

    /** 把上下文选择恢复成某条消息发送时的状态 */
    fun restoreContext(snapshot: ContextSnapshot) {
        selectedBookIds.value = snapshot.bookIds.toSet()
        selectedOutlineIds.value = snapshot.outlineIds.toSet()
        includeSummary.value = snapshot.includeSummary
        includeContent.value = snapshot.includeContent
        selectedChapterIds.value = snapshot.chapterIds.toSet()
        chapterAllSelected.value = false
        viewModelScope.launch {
            loadBookContextNow(snapshot.bookIds.firstOrNull())
            if (snapshot.chapterAll) {
                selectedChapterIds.value = contextChapterIds()
                chapterAllSelected.value = true
            } else {
                refreshChapterAllFlag()
            }
        }
    }

    /**
     * 重新生成：为某条用户消息追加一个新的回答版本（不重复写入用户消息）。
     * 与 deepseek 一致，可在多个版本间翻阅。
     */
    fun regenerate(userMessageId: Long) = launchGenerating {
        val sid = _sessionId.value ?: return@launchGenerating
        val modelId = activeModelId.value
        if (modelId <= 0L) {
            errorMessage.value = "请先在设置中配置并选择一个模型"
            return@launchGenerating
        }
        val target = userMessageId.takeIf { it > 0 } ?: return@launchGenerating
        val next = chatRepository.nextVariantIndex(target)
        runGeneration(sid, modelId, "", replyToUserId = target, historyEndId = target, variantIndex = next)
    }

    /** 保存对话中某条消息的编辑结果 */
    fun updateMessage(id: Long, content: String) = viewModelScope.launch {
        chatRepository.updateMessageContent(id, content)
    }

    /** 保存编辑后的回答，并让 AI 基于修改后的内容继续 */
    fun saveAndContinue(assistantMessageId: Long, content: String, userMessageId: Long) =
        launchGenerating {
            val sid = _sessionId.value ?: return@launchGenerating
            val modelId = activeModelId.value
            if (modelId <= 0L) {
                errorMessage.value = "请先在设置中配置并选择一个模型"
                return@launchGenerating
            }
            chatRepository.updateMessageContent(assistantMessageId, content)
            val next = chatRepository.nextVariantIndex(userMessageId)
            runGeneration(
                sid, modelId, "",
                replyToUserId = userMessageId,
                historyEndId = assistantMessageId,
                variantIndex = next
            )
        }

    /** 切换到某个回答版本 */
    fun switchVariant(userMessageId: Long, messageId: Long) = viewModelScope.launch {
        chatRepository.setActiveVariant(userMessageId, messageId)
    }

    fun deleteMessage(id: Long) = viewModelScope.launch { chatRepository.deleteMessage(id) }

    /** 收起 / 展开某条回答的正文，状态落库，再次进入会话时保持 */
    fun setContentCollapsed(messageId: Long, collapsed: Boolean) = viewModelScope.launch {
        chatRepository.setContentCollapsed(messageId, collapsed)
    }

    /** 当前上下文（所勾选书籍/章节/大纲）拼装出的文本，用于「文件清单」预览 */
    suspend fun contextPreview(): String = previewFor(currentSnapshot())

    /** 某个上下文快照拼装出的文本，用于已发送消息上的「文件清单」预览 */
    suspend fun previewFor(snapshot: ContextSnapshot): String = chatRepository.buildContext(
        bookIds = snapshot.bookIds,
        chapterIds = snapshot.chapterIds,
        outlineIds = snapshot.outlineIds,
        includeContent = snapshot.includeContent,
        includeSummary = snapshot.includeSummary
    )

    fun toggleVolume(volumeId: Long) {
        val all = _chapters.value.filter { it.volumeId == volumeId && it.id != fixedChapterId }.map { it.id }
        if (all.isEmpty()) return
        selectedChapterIds.value =
            if (all.all { it in selectedChapterIds.value }) selectedChapterIds.value - all.toSet()
            else selectedChapterIds.value + all.toSet()
        refreshChapterAllFlag()
    }

    /**
     * 运行一次生成：把输入打包成 [SendChatRequest] 交给 [SendChatMessageUseCase]，
     * 只负责把流式事件映射到 UI 状态（正文 / 思考增量、工具区块入队、错误）。
     * 返回是否生成成功（供「编辑后发送」决定是否丢弃旧回答）。
     */
    private suspend fun runGeneration(
        sid: Long,
        modelId: Long,
        systemPrompt: String,
        replyToUserId: Long,
        historyEndId: Long,
        variantIndex: Int
    ): Boolean {
        val myJob = currentCoroutineContext()[Job]
        sending.value = true
        pendingReplyToId.value = replyToUserId
        streamingContent.value = ""
        streamingReasoning.value = ""
        errorMessage.value = null
        var ok = false
        val contentBuf = StringBuilder()
        val reasoningBuf = StringBuilder()
        try {
            val request = SendChatRequest(
                sessionId = sid,
                modelId = modelId,
                systemPrompt = systemPrompt,
                selection = currentSnapshot(),
                replyToUserId = replyToUserId,
                historyEndId = historyEndId,
                variantIndex = variantIndex,
                writeOutlineTool = writeOutlineToolOn(),
                chapterImportTool = chapterImportToolOn(),
                firstRoundPrefix = firstRoundPrefixEnabled()
            )
            sendChatMessage(request).collect { event ->
                when (event) {
                    is ChatEvent.Chunk -> {
                        contentBuf.append(event.text)
                        streamingContent.value = contentBuf.toString()
                    }

                    is ChatEvent.ReasoningChunk -> {
                        reasoningBuf.append(event.text)
                        streamingReasoning.value = reasoningBuf.toString()
                    }

                    // AI 工具：回答里带可导入标记块时，按勾选的工具挨个入队，UI 逐个询问是否导入
                    is ChatEvent.OutlineBlocksReady ->
                        pendingImports.value = pendingImports.value + PendingImport.Outline(event.blocks)

                    is ChatEvent.ChapterBlocksReady ->
                        pendingImports.value = pendingImports.value + PendingImport.Chapters(event.blocks)

                    is ChatEvent.Error -> errorMessage.value = event.message

                    ChatEvent.Done -> ok = true
                }
            }
        } finally {
            // 仅当仍是当前这次生成时才复位：若已被「停止响应」取消或已被新任务接管，则不要覆盖新的生成态
            if (generateJob === myJob) {
                sending.value = false
                pendingReplyToId.value = 0L
                streamingContent.value = ""
                streamingReasoning.value = ""
            }
        }
        return ok
    }

    /**
     * 「首轮全选」是否生效：仅在章节内「跟书走」模式 + 用户开启该开关时启用，
     * 避免影响「跟章节」模式的常规上下文选择。
     */
    private suspend fun firstRoundPrefixEnabled(): Boolean =
        fixedChapterId != null && prefs.aiOutlineFirstRound.first() && prefs.aiFollowBook.first()

    /**
     * 「＋本章梗概」：勾选时随消息发送但不在气泡显示的附加提示词（含章节名与梗概）。
     * 与 content 一起落库，重建历史时拼回，避免每换一章就改写缓存前缀。
     */
    private suspend fun chapterBlockSuffix(): String {
        if (!currentSummaryOn.value) return ""
        val chapterId = fixedChapterId ?: return ""
        val ch = bookRepository.getChapter(chapterId) ?: return ""
        return buildString {
            append("【本章】").append(ch.title)
            if (ch.summary.isNotBlank()) append('\n').append("梗概：").append(ch.summary)
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (contains(id)) this - id else this + id

/** 待导入内容：AI 回答中解析出的可写入数据，逐个弹窗询问用户 */
sealed interface PendingImport {
    /** 写入大纲 */
    data class Outline(val blocks: List<AiOutlineBlock>) : PendingImport

    /** 导入章节梗概 */
    data class Chapters(val blocks: List<AiChapterBlock>) : PendingImport
}