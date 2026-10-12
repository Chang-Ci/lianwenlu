package com.lianwenqi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lianwenqi.app.data.entity.Chapter
import com.lianwenqi.app.data.entity.OutlineCategory
import com.lianwenqi.app.data.entity.OutlineItem
import com.lianwenqi.app.data.entity.Volume
import com.lianwenqi.app.util.FormatSettings
import com.lianwenqi.app.util.StyleSettings
import com.lianwenqi.app.util.TextTool

internal val TextColors = listOf(
    0xFF1A1A1A.toInt(), 0xFF37474F.toInt(), 0xFF8D6E63.toInt(), 0xFF00695C.toInt(),
    0xFF283593.toInt(), 0xFF6A1B9A.toInt(), 0xFFAD1457.toInt(), 0xFFC62828.toInt()
)

internal val BgColors = listOf(
    0xFFFFFFFF.toInt(), 0xFFF7F5F0.toInt(), 0xFFFFF8E1.toInt(), 0xFFE8F5E9.toInt(),
    0xFFE3F2FD.toInt(), 0xFFF3E5F5.toInt(), 0xFF1B1B1B.toInt(), 0xFFECEFF1.toInt()
)

/** 停靠在底部工具栏上方的面板最大高度，超出部分内部滚动。 */
private val PanelMaxHeight = 360.dp

@Composable
fun StylePanel(
    style: StyleSettings,
    onStyle: ((StyleSettings) -> StyleSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = PanelMaxHeight)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("样式", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("字体大小：${style.fontSizeSp.toInt()} sp")
        Slider(
            value = style.fontSizeSp,
            onValueChange = { v -> onStyle { it.copy(fontSizeSp = v) } },
            valueRange = 12f..40f
        )
        Text("字体")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("默认", "衬线", "等宽", "手写").forEach { label ->
                FilterChip(
                    selected = style.fontLabel == label,
                    onClick = { onStyle { it.copy(fontLabel = label, fontFamily = TextTool.fontFamilyOf(label)) } },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = style.bold, onClick = { onStyle { it.copy(bold = !it.bold) } },
                label = { Text("加粗") })
            FilterChip(selected = style.italic, onClick = { onStyle { it.copy(italic = !it.italic) } },
                label = { Text("斜体") })
            FilterChip(selected = style.center, onClick = { onStyle { it.copy(center = !it.center) } },
                label = { Text("居中") })
        }
        Spacer(Modifier.height(8.dp))
        Text("字体颜色")
        ColorRow(TextColors, style.textColor) { c -> onStyle { it.copy(textColor = c) } }
        Spacer(Modifier.height(8.dp))
        Text("背景颜色")
        ColorRow(BgColors, style.backgroundColor) { c -> onStyle { it.copy(backgroundColor = c) } }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            Box(
                Modifier
                    .size(if (c == selected) 34.dp else 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(c))
                    .clickable { onPick(c) }
            )
        }
    }
}

@Composable
fun FormatPanel(
    format: FormatSettings,
    onFormat: ((FormatSettings) -> FormatSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = PanelMaxHeight)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("排版设置", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("行间距：${"%.1f".format(format.lineSpacing)}")
        Slider(value = format.lineSpacing,
            onValueChange = { v -> onFormat { it.copy(lineSpacing = v) } },
            valueRange = 1.0f..3.0f)
        Text("左右边距：${format.paddingHorizontal} dp")
        Slider(value = format.paddingHorizontal.toFloat(),
            onValueChange = { v -> onFormat { it.copy(paddingHorizontal = v.toInt()) } },
            valueRange = 0f..64f)
        Text("底部边距：${format.paddingBottom} dp")
        Slider(value = format.paddingBottom.toFloat(),
            onValueChange = { v -> onFormat { it.copy(paddingBottom = v.toInt()) } },
            valueRange = 0f..120f)
        Text("首行缩进：${format.indent} 字符")
        Slider(value = format.indent.toFloat(),
            onValueChange = { v -> onFormat { it.copy(indent = v.toInt()) } },
            valueRange = 0f..4f)
        Text("排版空行：${format.blankLines}")
        Slider(value = format.blankLines.toFloat(),
            onValueChange = { v -> onFormat { it.copy(blankLines = v.toInt()) } },
            valueRange = 0f..2f)
        HorizontalDivider()
        SwitchRow("显示快速定位（记录上次退出光标）", format.quickLocate) { v ->
            onFormat { it.copy(quickLocate = v) }
        }
        SwitchRow("智能标点：成对标点补全", format.autoPair) { v ->
            onFormat { it.copy(autoPair = v) }
        }
        SwitchRow("智能标点：冒号紧随双引号", format.colonFollowQuote) { v ->
            onFormat { it.copy(colonFollowQuote = v) }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * 「查看」功能区：功能宫格（工具栏下方）。
 * 查找替换在工具栏上方另起一条独立输入条（FindReplaceBar），与输入法互不影响。
 */
@Composable
fun ViewGridPanel(
    wordCount: Int,
    onFindReplace: () -> Unit,
    onBookSearch: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            ViewTile("查找替换", Icons.Filled.FindReplace, null, Modifier.weight(1f), onFindReplace)
            ViewTile("全书查找", Icons.Filled.TravelExplore, null, Modifier.weight(1f), onBookSearch)
            ViewTile("字数统计", Icons.Filled.Numbers, "$wordCount 字", Modifier.weight(1f), null)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            ViewTile("粘贴", Icons.Filled.ContentPaste, null, Modifier.weight(1f), onPaste)
            ViewTile("全选", Icons.Filled.SelectAll, null, Modifier.weight(1f), onSelectAll)
            ViewTile("清空", Icons.Filled.DeleteSweep, null, Modifier.weight(1f), onClear)
        }
    }
}

@Composable
private fun ViewTile(
    label: String,
    icon: ImageVector,
    subtitle: String?,
    modifier: Modifier,
    onClick: (() -> Unit)?
) {
    Column(
        modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 工具栏上方的独立「查找/替换」输入条：输入法弹出时它保留，功能宫格收起。 */
@Composable
fun FindReplaceBar(
    content: String,
    onJump: (String, Boolean) -> Unit,
    onReplaceCurrent: (String, String) -> Unit,
    onReplaceAll: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val matches = remember(find, content) {
        if (find.isEmpty()) 0
        else Regex(Regex.escape(find)).findAll(content).count()
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "收起查找")
            }
            OutlinedTextField(
                value = find,
                onValueChange = { find = it },
                placeholder = { Text("查找内容") },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
            IconButton(onClick = { onJump(find, false) }, enabled = find.isNotEmpty()) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一个")
            }
            IconButton(onClick = { onJump(find, true) }, enabled = find.isNotEmpty()) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一个")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replace,
                onValueChange = { replace = it },
                placeholder = { Text("替换内容") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { onReplaceCurrent(find, replace) },
                enabled = find.isNotEmpty()
            ) { Text("替换") }
            TextButton(
                onClick = { onReplaceAll(find, replace) },
                enabled = find.isNotEmpty()
            ) { Text("全部") }
        }
        Text(
            if (find.isEmpty()) "请输入查找内容" else "共匹配 $matches 处",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ToolboxPanel(
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    onShareImage: () -> Unit,
    onShareTxt: () -> Unit,
    onShareWord: () -> Unit,
    onLocalPerson: (Int, Int) -> List<String>,
    onLocalPlace: (Int) -> List<String>,
    onAiGenerate: (String, (String) -> Unit) -> Unit,
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var count by remember { mutableStateOf(5) }
    var gender by remember { mutableStateOf(0) }
    var output by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = PanelMaxHeight)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("工具箱", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("分享章节", fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = onShareImage) { Text("生成图片") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onShareTxt) { Text("txt") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onShareWord) { Text("word") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("取名（数量：$count）", fontWeight = FontWeight.SemiBold)
        Slider(value = count.toFloat(), onValueChange = { count = it.toInt() },
            valueRange = 1f..20f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = gender == 0, onClick = { gender = 0 }, label = { Text("随机") })
            FilterChip(selected = gender == 1, onClick = { gender = 1 }, label = { Text("男") })
            FilterChip(selected = gender == 2, onClick = { gender = 2 }, label = { Text("女") })
        }
        Row(Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { output = onLocalPerson(count, gender).joinToString("、") }) {
                Text("人名生成")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { output = onLocalPlace(count).joinToString("、") }) {
                Text("地名生成")
            }
        }
        Row {
            OutlinedButton(onClick = {
                onAiGenerate("请生成$count 个中文小说${if (gender == 1) "男性" else if (gender == 2) "女性" else ""}人名，用顿号分隔，只输出名字。") { output = it }
            }) { Text("AI 人名生成") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                onAiGenerate("请生成$count 个中文小说地名，用顿号分隔，只输出地名。") { output = it }
            }) { Text("AI 地名生成") }
        }
        if (output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(output, Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x11000000))
                .padding(10.dp))
            Spacer(Modifier.height(6.dp))
            Button(onClick = { onInsert("\n" + output); output = "" }) { Text("插入到正文") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow("保持屏幕常亮", keepScreenOn, onKeepScreenOn)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
        Spacer(Modifier.height(16.dp))
    }
}

private data class CatalogGroup(val key: Long, val title: String, val chapters: List<Chapter>)

/** 左侧抽屉：顶部「大纲 / 目录」切换 */
@Composable
fun EditorDrawer(
    chapters: List<Chapter>,
    volumes: List<Volume>,
    outline: List<OutlineItem>,
    currentId: Long,
    wordCount: (String) -> Int,
    onSelect: (Long) -> Unit,
    onNew: (Long?) -> Unit,
    loadFields: suspend (String) -> List<String>,
    onSaveFields: (String, List<String>) -> Unit,
    onSaveOutline: (OutlineItem) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var editingOutline by remember { mutableStateOf<OutlineItem?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerTab("目录", tab == 0, Modifier.weight(1f)) { tab = 0 }
            DrawerTab("大纲", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }
        HorizontalDivider()
        if (tab == 0) {
            CatalogPanel(
                chapters = chapters,
                volumes = volumes,
                currentId = currentId,
                wordCount = wordCount,
                onSelect = onSelect,
                onNew = onNew
            )
        } else {
            OutlinePanel(outline, onEdit = { editingOutline = it })
        }
    }

    editingOutline?.let { item ->
        OutlineEditorDialog(
            categoryKey = item.category,
            categoryLabel = com.lianwenqi.app.data.entity.outlineCategoryLabel(item.category),
            initial = item,
            loadFields = loadFields,
            onSaveFields = { onSaveFields(item.category, it) },
            onDismiss = { editingOutline = null },
            onSave = { fs ->
                onSaveOutline(
                    item.copy(title = outlineTitleOf(fs), content = outlineContentOf(fs))
                )
                editingOutline = null
            }
        )
    }
}

@Composable
private fun DrawerTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 「大纲」页签：按分类展示作品大纲 / 人设 / 世界观 / 灵感；点击条目弹窗修改 */
@Composable
fun OutlinePanel(outline: List<OutlineItem>, onEdit: (OutlineItem) -> Unit) {
    // 记录被「收起」的分类，默认全部展开
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (outline.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无大纲内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            OutlineCategory.entries.forEach { cat ->
                val items = outline.filter { it.category == cat.name }
                if (items.isNotEmpty()) {
                    val isExpanded = collapsed[cat.name] != true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { collapsed[cat.name] = isExpanded }
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isExpanded) Icons.Filled.KeyboardArrowDown
                            else Icons.Filled.KeyboardArrowRight,
                            contentDescription = if (isExpanded) "收起" else "展开",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            cat.label,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${items.size}项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items.takeIf { isExpanded }.orEmpty().forEach { item ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onEdit(item) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.tag.isNotBlank()) {
                                    Text(
                                        item.tag,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    item.title,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = "修改",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.content.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    item.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 左侧目录：分卷/章节树（不含「作品相关」），卷显示「N章 M字」，章节显示「M字」 */
@Composable
fun CatalogPanel(
    chapters: List<Chapter>,
    volumes: List<Volume>,
    currentId: Long,
    wordCount: (String) -> Int,
    onSelect: (Long) -> Unit,
    onNew: (Long?) -> Unit,
    showNew: Boolean = true
) {
    var reverse by remember { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    val groups = remember(chapters, volumes, reverse) {
        val byVolume = chapters.groupBy { it.volumeId }
        val orderedVolumes = if (reverse) volumes.reversed() else volumes
        orderedVolumes.map { v ->
            CatalogGroup(v.id, v.title, byVolume[v.id].orEmpty().let { if (reverse) it.reversed() else it })
        }
    }
    val orphans = remember(chapters, reverse) {
        chapters.filter { it.volumeId == null }.let { if (reverse) it.reversed() else it }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { reverse = !reverse }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (reverse) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "倒序",
                    color = if (reverse) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider()

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            orphans.forEach { ch ->
                CatalogChapterRow(ch, ch.id == currentId, wordCount, onSelect, startPadding = 16.dp)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            groups.forEach { g ->
                val isExpanded = expanded[g.key] ?: g.chapters.any { it.id == currentId }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded[g.key] = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        g.title,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${g.chapters.size}章 ${g.chapters.sumOf { wordCount(it.content) }}字",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isExpanded) {
                    g.chapters.forEach { ch ->
                        CatalogChapterRow(ch, ch.id == currentId, wordCount, onSelect, startPadding = 38.dp)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (showNew) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onNew(g.key) }
                                .padding(start = 38.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("新建章节", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!showNew) return@Box
                Text(
                    "＋ 新建章节",
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNew(null) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CatalogChapterRow(
    chapter: Chapter,
    selected: Boolean,
    wordCount: (String) -> Int,
    onSelect: (Long) -> Unit,
    startPadding: Dp
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(chapter.id) }
            .padding(start = startPadding, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            chapter.title,
            modifier = Modifier.weight(1f),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${wordCount(chapter.content)}字",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}