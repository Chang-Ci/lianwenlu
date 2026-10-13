package com.lianwenqi.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianwenqi.app.AppContainer
import com.lianwenqi.app.data.AiChapterImporter
import com.lianwenqi.app.data.AiOutlineImporter
import com.lianwenqi.app.data.SortMode
import com.lianwenqi.app.data.entity.Book
import com.lianwenqi.app.data.entity.BookGroup
import com.lianwenqi.app.ui.appViewModel
import com.lianwenqi.app.ui.components.BookFormResult
import com.lianwenqi.app.util.AI_CHAPTER_TOOL_PROMPT
import com.lianwenqi.app.util.AI_OUTLINE_TOOL_PROMPT
import com.lianwenqi.app.util.AI_TOOLS
import com.lianwenqi.app.util.AI_TOOL_IMPORT_CHAPTER
import com.lianwenqi.app.util.AI_TOOL_WRITE_OUTLINE
import com.lianwenqi.app.util.FileUtil
import com.lianwenqi.app.util.parseAiChapterBlocks
import com.lianwenqi.app.util.parseAiOutlineBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiToolsViewModel(private val container: AppContainer) : ViewModel() {
    private val prefs = container.prefs
    private val bookRepository = container.bookRepository
    private val importer = AiOutlineImporter(container.prefs, container.outlineRepository)
    private val chapterImporter = AiChapterImporter(container.bookRepository)

    val books: StateFlow<List<Book>> =
        bookRepository.observeBooks(SortMode.TIME, false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 书籍分组（选书弹窗的分组筛选用） */
    val groups: StateFlow<List<BookGroup>> =
        bookRepository.observeGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outlineToolEnabled: StateFlow<Boolean> =
        prefs.aiToolOutlineEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val chapterToolEnabled: StateFlow<Boolean> =
        prefs.aiToolChapterEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setOutlineToolEnabled(v: Boolean) = viewModelScope.launch { prefs.setAiToolOutlineEnabled(v) }

    fun setChapterToolEnabled(v: Boolean) = viewModelScope.launch { prefs.setAiToolChapterEnabled(v) }

    /** 完整表单新建书籍，返回新书 id */
    suspend fun createBookFull(r: BookFormResult): Long = bookRepository.createBook(
        title = r.title.trim(),
        intro = r.intro.trim(),
        coverPath = r.coverPath,
        coverColor = r.coverColor,
        volumePrefix = r.volumePrefix,
        chapterPrefix = r.chapterPrefix,
        groupId = r.groupId
    )

    /** 复制封面图到应用私有目录，返回本地路径 */
    suspend fun pickCover(uri: android.net.Uri): String? =
        withContext(Dispatchers.IO) { FileUtil.copyToInternal(container.context, uri) }

    /** 新建分组并返回新分组 id（书籍表单内现场建组用） */
    suspend fun createGroupAndGet(name: String): Long = bookRepository.createGroup(name)

    /** 把文本中的大纲区块导入指定书籍；返回 null 表示文本里没有可解析的内容 */
    suspend fun importOutline(bookId: Long, text: String): String? {
        val blocks = parseAiOutlineBlocks(text)
        if (blocks.isEmpty()) return null
        val result = importer.import(bookId, blocks)
        return buildString {
            append("已写入 ${result.items} 条大纲")
            if (result.newCategories.isNotEmpty()) {
                append("，新建标签：${result.newCategories.joinToString("、")}")
            }
        }
    }

    /** 把文本中的章节梗概区块导入指定书籍；返回 null 表示文本里没有可解析的内容 */
    suspend fun importChapters(bookId: Long, text: String): String? {
        val blocks = parseAiChapterBlocks(text)
        if (blocks.isEmpty()) return null
        val result = chapterImporter.import(bookId, blocks)
        return buildString {
            append("已导入章节梗概：新建 ${result.created} 章")
            if (result.updated > 0) append("，更新 ${result.updated} 章")
            if (result.newVolumes.isNotEmpty()) {
                append("，新建分卷：${result.newVolumes.joinToString("、")}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsScreen(onBack: () -> Unit) {
    val vm = appViewModel { AiToolsViewModel(it) }
    val outlineEnabled by vm.outlineToolEnabled.collectAsState()
    val chapterEnabled by vm.chapterToolEnabled.collectAsState()
    // 当前展开的工具 id 集合（默认全部展开）
    var expandedIds by remember { mutableStateOf(AI_TOOLS.map { it.id }.toSet()) }
    var showImport by remember { mutableStateOf(false) }
    var showChapterImport by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 工具") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            // 写入大纲工具（以后新增工具按同样结构追加）
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedIds = if (AI_TOOL_WRITE_OUTLINE in expandedIds)
                                    expandedIds - AI_TOOL_WRITE_OUTLINE else expandedIds + AI_TOOL_WRITE_OUTLINE
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("写入大纲工具", fontWeight = FontWeight.SemiBold)
                            Text(
                                "AI 生成的人设 / 世界观等可按固定格式解析并写入作品大纲",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (AI_TOOL_WRITE_OUTLINE in expandedIds) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    if (AI_TOOL_WRITE_OUTLINE in expandedIds) {
                        HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("启用该工具")
                                Text(
                                    "启用后可在 AI 聊天页勾选，AI 回答后可一键写入大纲",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = outlineEnabled, onCheckedChange = { vm.setOutlineToolEnabled(it) })
                        }
                        HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(AnnotatedString(AI_OUTLINE_TOOL_PROMPT))
                                    Toast.makeText(context, "工具提示词已复制", Toast.LENGTH_SHORT).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("复制工具提示词", Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showImport = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("导入大纲")
                                Text(
                                    "粘贴含 <<<AI_OUTLINE_JSON_V1>>> 标记的文本，解析后写入作品大纲",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 章节梗概导入工具
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedIds = if (AI_TOOL_IMPORT_CHAPTER in expandedIds)
                                    expandedIds - AI_TOOL_IMPORT_CHAPTER else expandedIds + AI_TOOL_IMPORT_CHAPTER
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("章节梗概导入工具", fontWeight = FontWeight.SemiBold)
                            Text(
                                "AI 生成的章节名 / 梗概可按固定格式解析，批量写入书籍分卷与章节",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (AI_TOOL_IMPORT_CHAPTER in expandedIds) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                    if (AI_TOOL_IMPORT_CHAPTER in expandedIds) {
                        HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("启用该工具")
                                Text(
                                    "启用后可在 AI 聊天页勾选，AI 回答后可一键导入章节梗概",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = chapterEnabled, onCheckedChange = { vm.setChapterToolEnabled(it) })
                        }
                        HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(AnnotatedString(AI_CHAPTER_TOOL_PROMPT))
                                    Toast.makeText(context, "工具提示词已复制", Toast.LENGTH_SHORT).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("复制工具提示词", Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showChapterImport = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("导入章节梗概")
                                Text(
                                    "粘贴含 <<<AI_CHAPTER_JSON_V1>>> 标记的文本，解析后写入分卷与章节",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImport) {
        AiOutlineImportDialog(
            vm = vm,
            onDismiss = { showImport = false }
        )
    }

    if (showChapterImport) {
        AiChapterImportDialog(
            vm = vm,
            onDismiss = { showChapterImport = false }
        )
    }
}

/** 导入大纲弹窗：选书（可现场新建）+ 粘贴文本 + 解析导入 */
@Composable
private fun AiOutlineImportDialog(vm: AiToolsViewModel, onDismiss: () -> Unit) {
    val books by vm.books.collectAsState()
    val groups by vm.groups.collectAsState()
    var text by remember { mutableStateOf("") }
    var targetBookId by remember { mutableStateOf<Long?>(null) }
    var showBookPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val blockCount = remember(text) { parseAiOutlineBlocks(text).size }
    val targetBook = books.firstOrNull { it.id == targetBookId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入大纲") },
        text = {
            // 文本框固定高度并内部滚动，避免长文本时输入框顶到键盘下方遮住光标
            Column {
                OutlinedButton(onClick = { showBookPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        targetBook?.let { "写入到：《${it.title}》" } ?: "选择要写入的书籍",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("粘贴包含大纲标记的文本") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (blockCount > 0) "已识别 $blockCount 个大纲区块" else "未识别到大纲区块",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (blockCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !importing && targetBookId != null && blockCount > 0,
                onClick = {
                    val bookId = targetBookId ?: return@Button
                    importing = true
                    scope.launch {
                        val msg = vm.importOutline(bookId, text)
                        importing = false
                        if (msg == null) {
                            error = "没有解析到可导入的大纲内容"
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    }
                }
            ) { Text(if (importing) "导入中…" else "导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showBookPicker) {
        BookPickerDialog(
            books = books,
            groups = groups,
            selectedId = targetBookId,
            onSelect = { targetBookId = it },
            onCreateBook = { vm.createBookFull(it) },
            onPickCover = { uri, cb -> scope.launch { cb(vm.pickCover(uri)) } },
            onCreateGroup = { vm.createGroupAndGet(it) },
            onDismiss = { showBookPicker = false }
        )
    }
}

/** 导入章节梗概弹窗：选书（可现场新建）+ 粘贴文本 + 解析导入 */
@Composable
private fun AiChapterImportDialog(vm: AiToolsViewModel, onDismiss: () -> Unit) {
    val books by vm.books.collectAsState()
    val groups by vm.groups.collectAsState()
    var text by remember { mutableStateOf("") }
    var targetBookId by remember { mutableStateOf<Long?>(null) }
    var showBookPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val blockCount = remember(text) { parseAiChapterBlocks(text).size }
    val targetBook = books.firstOrNull { it.id == targetBookId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入章节梗概") },
        text = {
            // 文本框固定高度并内部滚动，避免长文本时输入框顶到键盘下方遮住光标
            Column {
                OutlinedButton(onClick = { showBookPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        targetBook?.let { "写入到：《${it.title}》" } ?: "选择要写入的书籍",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("粘贴包含章节梗概标记的文本") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (blockCount > 0) "已识别 $blockCount 个章节区块" else "未识别到章节区块",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (blockCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !importing && targetBookId != null && blockCount > 0,
                onClick = {
                    val bookId = targetBookId ?: return@Button
                    importing = true
                    scope.launch {
                        val msg = vm.importChapters(bookId, text)
                        importing = false
                        if (msg == null) {
                            error = "没有解析到可导入的章节梗概内容"
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    }
                }
            ) { Text(if (importing) "导入中…" else "导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showBookPicker) {
        BookPickerDialog(
            books = books,
            groups = groups,
            selectedId = targetBookId,
            onSelect = { targetBookId = it },
            onCreateBook = { vm.createBookFull(it) },
            onPickCover = { uri, cb -> scope.launch { cb(vm.pickCover(uri)) } },
            onCreateGroup = { vm.createGroupAndGet(it) },
            onDismiss = { showBookPicker = false }
        )
    }
}
