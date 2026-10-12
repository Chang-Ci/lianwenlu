package com.lianwenqi.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lianwenqi.app.data.entity.Book
import com.lianwenqi.app.data.entity.BookGroup
import com.lianwenqi.app.util.buildTitle
import kotlinx.coroutines.launch
import java.io.File

/** 「新建 / 编辑书籍」表单提交的结果 */
data class BookFormResult(
    val title: String,
    val intro: String,
    val coverPath: String?,
    val coverColor: Int,
    val volumePrefix: String,
    val chapterPrefix: String,
    /** 所属分组 id，null 表示不分组 */
    val groupId: Long?
)

/**
 * 新建 / 编辑书籍的完整表单弹窗（书名、简介、封面、分组、标题前缀）。
 * [initial] 非空时为编辑模式，各字段预填该书现有值。
 */
@Composable
fun BookFormDialog(
    dialogTitle: String,
    initial: Book?,
    groups: List<BookGroup>,
    onPickCover: (Uri, (String?) -> Unit) -> Unit,
    /** 现场新建分组，返回新分组 id（创建后自动选中） */
    onCreateGroup: suspend (String) -> Long,
    onConfirm: (BookFormResult) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var intro by remember { mutableStateOf(initial?.intro ?: "") }
    var coverPath by remember { mutableStateOf(initial?.coverPath) }
    var color by remember { mutableStateOf(initial?.coverColor ?: CoverColors.first()) }
    var volumePrefix by remember { mutableStateOf(initial?.volumePrefix ?: "第%n卷") }
    var chapterPrefix by remember { mutableStateOf(initial?.chapterPrefix ?: "第%n章") }
    var groupId by remember { mutableStateOf(initial?.groupId) }
    // 现场新建分组：输入框非 null 时展示
    var newGroupName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onPickCover(it) { path -> coverPath = path } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (initial == null) Icons.Filled.AutoStories else Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        dialogTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (initial == null) "填写基础信息，即刻开始创作" else "修改书籍信息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 封面 + 配色
                Row {
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(color))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { picker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val path = coverPath
                        if (path.isNullOrBlank()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.92f),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "选择封面",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        } else {
                            AsyncImage(
                                model = File(path),
                                contentDescription = "封面预览",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "封面配色",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        CoverColors.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { c ->
                                    ColorDot(color = c, selected = c == color) {
                                        color = c
                                        coverPath = null
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            if (coverPath == null) "点击左侧色块或选择图片" else "已选择自定义封面",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SectionLabel("基本信息")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    placeholder = { Text("未命名书籍") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = intro,
                    onValueChange = { intro = it },
                    label = { Text("简介（可选）") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("所属分组")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = groupId == null,
                        onClick = { groupId = null },
                        label = { Text("不分组") }
                    )
                    groups.forEach { g ->
                        FilterChip(
                            selected = groupId == g.id,
                            onClick = { groupId = g.id },
                            label = { Text(g.name) }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { newGroupName = "" },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Add, null, Modifier.size(14.dp))
                                Text("新建分组")
                            }
                        }
                    )
                }
                // 现场新建分组：输入名称后确认，自动选中新分组
                newGroupName?.let { pending ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = pending,
                            onValueChange = { newGroupName = it },
                            label = { Text("分组名称") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val name = pending.trim()
                                if (name.isNotEmpty()) {
                                    scope.launch {
                                        groupId = onCreateGroup(name)
                                        newGroupName = null
                                    }
                                }
                            }
                        ) { Text("确定") }
                        TextButton(onClick = { newGroupName = null }) { Text("取消") }
                    }
                }

                SectionLabel("标题前缀")
                Text(
                    "新建分卷 / 章节时自动顺延编号：%n 阿拉伯数字，%N 中文数字。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = volumePrefix,
                    onValueChange = { volumePrefix = it },
                    label = { Text("分卷") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        Text("示例：${buildTitle(volumePrefix.ifBlank { "第%n卷" }, 1)}、${buildTitle(volumePrefix.ifBlank { "第%n卷" }, 2)}")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chapterPrefix,
                    onValueChange = { chapterPrefix = it },
                    label = { Text("章节") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        Text("示例：${buildTitle(chapterPrefix.ifBlank { "第%n章" }, 1)}、${buildTitle(chapterPrefix.ifBlank { "第%n章" }, 2)}")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            BookFormResult(
                                title = title.trim(),
                                intro = intro.trim(),
                                coverPath = coverPath,
                                coverColor = color,
                                volumePrefix = volumePrefix.ifBlank { "第%n卷" },
                                chapterPrefix = chapterPrefix.ifBlank { "第%n章" },
                                groupId = groupId
                            )
                        )
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (initial == null) "创建" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("取消") }
        }
    )
}

/** 弹窗内的小节标题 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** 封面配色圆点，选中时显示主题色描边与对勾 */
@Composable
private fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(color))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
