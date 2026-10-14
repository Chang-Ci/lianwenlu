package com.lianwenqi.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lianwenqi.app.data.entity.ContextSnapshot
import com.lianwenqi.app.data.entity.decodeContextSnapshot
import com.lianwenqi.app.util.FormatSettings
import com.lianwenqi.app.util.StyleSettings
import com.lianwenqi.app.util.TextTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 全局偏好设置：夜间模式、排序、排版、样式等 */
class Prefs(private val context: Context) {

    private object K {
        val nightMode = booleanPreferencesKey("night_mode")
        val sortMode = stringPreferencesKey("sort_mode")
        val sortAsc = booleanPreferencesKey("sort_asc")
        val listMode = booleanPreferencesKey("list_mode")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")

        val lineSpacing = floatPreferencesKey("fmt_line_spacing")
        val indent = intPreferencesKey("fmt_indent")
        val blankLines = intPreferencesKey("fmt_blank_lines")
        val quickLocate = booleanPreferencesKey("fmt_quick_locate")
        val autoPair = booleanPreferencesKey("fmt_auto_pair")
        val colonQuote = booleanPreferencesKey("fmt_colon_quote")
        val padH = intPreferencesKey("fmt_pad_h")
        val padBottom = intPreferencesKey("fmt_pad_bottom")

        val fontSize = floatPreferencesKey("style_font_size")
        val fontLabel = stringPreferencesKey("style_font_label")
        val bold = booleanPreferencesKey("style_bold")
        val italic = booleanPreferencesKey("style_italic")
        val center = booleanPreferencesKey("style_center")
        val textColor = intPreferencesKey("style_text_color")
        val bgColor = intPreferencesKey("style_bg_color")

        val activeModelId = longPreferencesKey("active_model_id")
        val lastBookId = longPreferencesKey("last_book_id")
        val lastChapterId = longPreferencesKey("last_chapter_id")

        val readerPageMode = intPreferencesKey("reader_page_mode")
        val readerImmersive = booleanPreferencesKey("reader_immersive")

        // 章节内 AI 会话的归属：false 跟章节，true 跟书
        val aiFollowBook = booleanPreferencesKey("ai_session_follow_book")

        // 首轮全选大纲：跟书模式下仅把大纲写进首轮前缀，后续轮次复用不再改写
        val aiOutlineFirstRound = booleanPreferencesKey("ai_outline_first_round")

        // AI 工具页：写入大纲工具是否启用（默认启用）
        val aiToolOutlineEnabled = booleanPreferencesKey("ai_tool_outline_enabled")

        // AI 工具页：章节梗概导入工具是否启用（默认启用）
        val aiToolChapterEnabled = booleanPreferencesKey("ai_tool_chapter_enabled")

        // AI 聊天页勾选的 AI 工具 id 集合（可多选，跨会话保持）
        val aiChatTools = stringSetPreferencesKey("ai_chat_tools")

        // 前缀词管理：上下文前缀化 / 大纲前缀化（均默认开启）
        val prefixContextEnabled = booleanPreferencesKey("prefix_context_enabled")
        val prefixOutlineEnabled = booleanPreferencesKey("prefix_outline_enabled")

        // 前缀词管理：自定义前缀（纯文本）
        val prefixCustomText = stringPreferencesKey("prefix_custom_text")

        // 阅读器独立的样式与排版：与编辑页各存一套，互不影响
        val rFontSize = floatPreferencesKey("reader_style_font_size")
        val rFontLabel = stringPreferencesKey("reader_style_font_label")
        val rBold = booleanPreferencesKey("reader_style_bold")
        val rItalic = booleanPreferencesKey("reader_style_italic")
        val rCenter = booleanPreferencesKey("reader_style_center")
        val rTextColor = intPreferencesKey("reader_style_text_color")
        val rBgColor = intPreferencesKey("reader_style_bg_color")

        val rLineSpacing = floatPreferencesKey("reader_fmt_line_spacing")
        val rPadH = intPreferencesKey("reader_fmt_pad_h")
        val rPadBottom = intPreferencesKey("reader_fmt_pad_bottom")
        val rPadV = intPreferencesKey("reader_fmt_pad_v")
        val rIndent = intPreferencesKey("reader_fmt_indent")
        val rBlankLines = intPreferencesKey("reader_fmt_blank_lines")
    }

    val nightMode: Flow<Boolean> = context.dataStore.data.map { it[K.nightMode] ?: false }
    val sortMode: Flow<String> = context.dataStore.data.map { it[K.sortMode] ?: "TIME" }
    val sortAsc: Flow<Boolean> = context.dataStore.data.map { it[K.sortAsc] ?: false }
    val listMode: Flow<Boolean> = context.dataStore.data.map { it[K.listMode] ?: true }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[K.keepScreenOn] ?: false }
    val activeModelId: Flow<Long> = context.dataStore.data.map { it[K.activeModelId] ?: 0L }
    val lastBookId: Flow<Long> = context.dataStore.data.map { it[K.lastBookId] ?: 0L }
    val lastChapterId: Flow<Long> = context.dataStore.data.map { it[K.lastChapterId] ?: 0L }

    /** 阅读器翻页方式：0 上下滚动，1 左右翻页 */
    val readerPageMode: Flow<Int> = context.dataStore.data.map { it[K.readerPageMode] ?: 0 }

    /** 阅读器全屏（隐藏系统状态栏与导航栏） */
    val readerImmersive: Flow<Boolean> = context.dataStore.data.map { it[K.readerImmersive] ?: true }

    /** 章节内 AI 会话归属：false 跟章节（默认），true 跟书 */
    val aiFollowBook: Flow<Boolean> = context.dataStore.data.map { it[K.aiFollowBook] ?: false }

    /** 首轮全选大纲：跟书模式下仅首轮把大纲写进前缀（默认关闭） */
    val aiOutlineFirstRound: Flow<Boolean> = context.dataStore.data.map { it[K.aiOutlineFirstRound] ?: false }

    /** 「写入大纲工具」是否启用（默认启用；关闭后不出现在聊天页工具多选里） */
    val aiToolOutlineEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.aiToolOutlineEnabled] ?: true }

    /** 「章节梗概导入工具」是否启用（默认启用；关闭后不出现在聊天页工具多选里） */
    val aiToolChapterEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.aiToolChapterEnabled] ?: true }

    /** AI 聊天页勾选的 AI 工具 id 集合 */
    val aiChatTools: Flow<Set<String>> = context.dataStore.data.map { it[K.aiChatTools] ?: emptySet() }

    /** 「上下文前缀化」：勾选的书籍 / 章节写进会话前缀后只增不减（默认开启） */
    val prefixContextEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[K.prefixContextEnabled] ?: true }

    /** 「大纲前缀化」：勾选的大纲写进会话前缀后只增不减（默认开启） */
    val prefixOutlineEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[K.prefixOutlineEnabled] ?: true }

    /** 「自定义前缀」：纯文本，新建会话前缀时取一次并冻结在该会话中 */
    val prefixCustomText: Flow<String> = context.dataStore.data.map { it[K.prefixCustomText] ?: "" }

    /** 阅读器排版：与编辑页排版互相独立 */
    val readerFormat: Flow<FormatSettings> = context.dataStore.data.map {
        FormatSettings(
            lineSpacing = it[K.rLineSpacing] ?: 1.5f,
            paddingHorizontal = it[K.rPadH] ?: 16,
            paddingBottom = it[K.rPadBottom] ?: 24,
            paddingVertical = it[K.rPadV] ?: 12,
            indent = it[K.rIndent] ?: 2,
            blankLines = it[K.rBlankLines] ?: 0
        )
    }

    /** 阅读器样式：与编辑页样式互相独立 */
    val readerStyle: Flow<StyleSettings> = context.dataStore.data.map {
        val label = it[K.rFontLabel] ?: "默认"
        StyleSettings(
            fontSizeSp = it[K.rFontSize] ?: 17f,
            fontFamily = TextTool.fontFamilyOf(label),
            fontLabel = label,
            bold = it[K.rBold] ?: false,
            italic = it[K.rItalic] ?: false,
            center = it[K.rCenter] ?: false,
            textColor = it[K.rTextColor] ?: 0xFF1A1A1A.toInt(),
            backgroundColor = it[K.rBgColor] ?: 0xFFFFFFFF.toInt()
        )
    }

    val format: Flow<com.lianwenqi.app.util.FormatSettings> = context.dataStore.data.map {
        com.lianwenqi.app.util.FormatSettings(
            lineSpacing = it[K.lineSpacing] ?: 1.5f,
            paddingHorizontal = it[K.padH] ?: 16,
            paddingBottom = it[K.padBottom] ?: 24,
            indent = it[K.indent] ?: 2,
            blankLines = it[K.blankLines] ?: 0,
            quickLocate = it[K.quickLocate] ?: true,
            autoPair = it[K.autoPair] ?: true,
            colonFollowQuote = it[K.colonQuote] ?: true
        )
    }

    val style: Flow<com.lianwenqi.app.util.StyleSettings> = context.dataStore.data.map {
        val label = it[K.fontLabel] ?: "默认"
        com.lianwenqi.app.util.StyleSettings(
            fontSizeSp = it[K.fontSize] ?: 17f,
            fontFamily = com.lianwenqi.app.util.TextTool.fontFamilyOf(label),
            fontLabel = label,
            bold = it[K.bold] ?: false,
            italic = it[K.italic] ?: false,
            center = it[K.center] ?: false,
            textColor = it[K.textColor] ?: 0xFF1A1A1A.toInt(),
            backgroundColor = it[K.bgColor] ?: 0xFFFFFFFF.toInt()
        )
    }

    suspend fun setNightMode(v: Boolean) = writePrefs { it[K.nightMode] = v }
    suspend fun setSortMode(v: String) = writePrefs { it[K.sortMode] = v }
    suspend fun setSortAsc(v: Boolean) = writePrefs { it[K.sortAsc] = v }
    suspend fun setListMode(v: Boolean) = writePrefs { it[K.listMode] = v }
    suspend fun setKeepScreenOn(v: Boolean) = writePrefs { it[K.keepScreenOn] = v }
    suspend fun setActiveModelId(v: Long) = writePrefs { it[K.activeModelId] = v }
    suspend fun setReaderPageMode(v: Int) = writePrefs { it[K.readerPageMode] = v }
    suspend fun setReaderImmersive(v: Boolean) = writePrefs { it[K.readerImmersive] = v }
    suspend fun setAiFollowBook(v: Boolean) = writePrefs { it[K.aiFollowBook] = v }
    suspend fun setAiOutlineFirstRound(v: Boolean) = writePrefs { it[K.aiOutlineFirstRound] = v }
    suspend fun setAiToolOutlineEnabled(v: Boolean) = writePrefs { it[K.aiToolOutlineEnabled] = v }
    suspend fun setAiToolChapterEnabled(v: Boolean) = writePrefs { it[K.aiToolChapterEnabled] = v }
    suspend fun setAiChatTools(v: Set<String>) = writePrefs { it[K.aiChatTools] = v }
    suspend fun setPrefixContextEnabled(v: Boolean) = writePrefs { it[K.prefixContextEnabled] = v }
    suspend fun setPrefixOutlineEnabled(v: Boolean) = writePrefs { it[K.prefixOutlineEnabled] = v }
    suspend fun setPrefixCustomText(v: String) = writePrefs { it[K.prefixCustomText] = v }
    suspend fun setLastOpened(bookId: Long, chapterId: Long) = writePrefs {
        it[K.lastBookId] = bookId
        it[K.lastChapterId] = chapterId
    }

    suspend fun updateFormat(block: (com.lianwenqi.app.util.FormatSettings) -> com.lianwenqi.app.util.FormatSettings) {
        val cur = format.firstValue()
        val next = block(cur)
        writePrefs {
            it[K.lineSpacing] = next.lineSpacing
            it[K.padH] = next.paddingHorizontal
            it[K.padBottom] = next.paddingBottom
            it[K.indent] = next.indent
            it[K.blankLines] = next.blankLines
            it[K.quickLocate] = next.quickLocate
            it[K.autoPair] = next.autoPair
            it[K.colonQuote] = next.colonFollowQuote
        }
    }

    suspend fun updateStyle(block: (com.lianwenqi.app.util.StyleSettings) -> com.lianwenqi.app.util.StyleSettings) {
        val next = block(style.firstValue())
        writePrefs {
            it[K.fontSize] = next.fontSizeSp
            it[K.fontLabel] = next.fontLabel
            it[K.bold] = next.bold
            it[K.italic] = next.italic
            it[K.center] = next.center
            it[K.textColor] = next.textColor
            it[K.bgColor] = next.backgroundColor
        }
    }

    suspend fun updateReaderFormat(block: (FormatSettings) -> FormatSettings) {
        val next = block(readerFormat.firstValue())
        writePrefs {
            it[K.rLineSpacing] = next.lineSpacing
            it[K.rPadH] = next.paddingHorizontal
            it[K.rPadBottom] = next.paddingBottom
            it[K.rPadV] = next.paddingVertical
            it[K.rIndent] = next.indent
            it[K.rBlankLines] = next.blankLines
        }
    }

    suspend fun updateReaderStyle(block: (StyleSettings) -> StyleSettings) {
        val next = block(readerStyle.firstValue())
        writePrefs {
            it[K.rFontSize] = next.fontSizeSp
            it[K.rFontLabel] = next.fontLabel
            it[K.rBold] = next.bold
            it[K.rItalic] = next.italic
            it[K.rCenter] = next.center
            it[K.rTextColor] = next.textColor
            it[K.rBgColor] = next.backgroundColor
        }
    }

    private suspend fun writePrefs(
        block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit
    ) {
        context.dataStore.edit(block)
    }

    // ---------------- AI 上下文选择记忆 ----------------

    /** 某本书上次在 AI 侧栏选中的章节 / 大纲与「仅梗概 / 仅正文」开关 */
    suspend fun aiContextMemory(bookId: Long): ContextSnapshot? {
        val raw = context.dataStore.data.first()[aiContextKey(bookId)] ?: return null
        return decodeContextSnapshot(raw)
    }

    suspend fun setAiContextMemory(bookId: Long, snapshot: ContextSnapshot) {
        writePrefs { it[aiContextKey(bookId)] = snapshot.encode() }
    }

    private fun aiContextKey(bookId: Long) = stringPreferencesKey("ai_ctx_$bookId")

    // ---------------- 大纲字段表 ----------------

    /** 大纲「表」：新增条目时预填的字段名列表，按 书籍 + 分类 分别保存 */
    suspend fun outlineFields(bookId: Long, category: String): List<String>? {
        val raw = context.dataStore.data.first()[outlineFieldsKey(bookId, category)] ?: return null
        return runCatching {
            Json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrNull()
    }

    suspend fun setOutlineFields(bookId: Long, category: String, names: List<String>) {
        val json = Json.encodeToString(ListSerializer(String.serializer()), names)
        writePrefs { it[outlineFieldsKey(bookId, category)] = json }
    }

    private fun outlineFieldsKey(bookId: Long, category: String) =
        stringPreferencesKey("outline_fields_${bookId}_$category")

    // ---------------- 大纲分类标签 ----------------

    /** 某本书的大纲分类标签（存储值有序列表）；未自定义过时返回 null */
    suspend fun outlineCategories(bookId: Long): List<String>? {
        val raw = context.dataStore.data.first()[outlineCategoriesKey(bookId)] ?: return null
        return runCatching {
            Json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrNull()
    }

    suspend fun setOutlineCategories(bookId: Long, keys: List<String>) {
        val json = Json.encodeToString(ListSerializer(String.serializer()), keys)
        writePrefs { it[outlineCategoriesKey(bookId)] = json }
    }

    private fun outlineCategoriesKey(bookId: Long) =
        stringPreferencesKey("outline_categories_$bookId")

    private suspend fun <T> Flow<T>.firstValue(): T = first()
}