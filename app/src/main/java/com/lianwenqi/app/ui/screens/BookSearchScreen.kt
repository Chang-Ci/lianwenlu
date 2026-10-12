package com.lianwenqi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianwenqi.app.AppContainer
import com.lianwenqi.app.data.entity.Book
import com.lianwenqi.app.data.entity.Chapter
import com.lianwenqi.app.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BookSearchViewModel(container: AppContainer, val bookId: Long) : ViewModel() {
    private val repo = container.repository

    val book: StateFlow<Book?> =
        repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val chapters: StateFlow<List<Chapter>> =
        repo.observeChapters(bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** 全书查找结果：章节、命中次数、首个命中处的片段 */
private data class BookSearchHit(val chapter: Chapter, val count: Int, val snippet: String)

private fun snippetOf(content: String, keyword: String): String {
    val at = content.indexOf(keyword)
    if (at < 0) return content.take(30).replace("\n", " ")
    val start = (at - 12).coerceAtLeast(0)
    val end = (at + keyword.length + 18).coerceAtMost(content.length)
    return (if (start > 0) "…" else "") + content.substring(start, end).replace("\n", " ") +
        (if (end < content.length) "…" else "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSearchScreen(
    bookId: Long,
    onBack: () -> Unit,
    onOpenChapter: (Long) -> Unit
) {
    val vm = appViewModel(key = "book-search-$bookId") { BookSearchViewModel(it, bookId) }
    val book by vm.book.collectAsState()
    val chapters by vm.chapters.collectAsState()

    var keyword by remember { mutableStateOf("") }
    var searchTitle by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }

    val hits = remember(chapters, keyword, searchTitle) {
        if (keyword.isBlank()) emptyList()
        else chapters.mapNotNull { ch ->
            val inContent = ch.content.split(keyword).size - 1
            val inTitle = if (searchTitle) ch.title.split(keyword).size - 1 else 0
            val count = inContent + inTitle
            if (count > 0) BookSearchHit(ch, count, snippetOf(ch.content, keyword)) else null
        }
    }
    val total = hits.sumOf { it.count }
    val safeSelected = selected.coerceIn(0, (hits.size - 1).coerceAtLeast(0))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全书查找-《${book?.title ?: ""}》", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (hits.isEmpty()) "" else "${safeSelected + 1}/${hits.size}",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    enabled = safeSelected > 0,
                    onClick = { selected = safeSelected - 1 }
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一条") }
                IconButton(
                    enabled = safeSelected < hits.size - 1,
                    onClick = { selected = safeSelected + 1 }
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一条") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = hits.isNotEmpty(),
                    onClick = { hits.getOrNull(safeSelected)?.let { onOpenChapter(it.chapter.id) } }
                ) { Text("打开章节") }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it; selected = 0 },
                placeholder = { Text("待查找文本") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = searchTitle, onCheckedChange = { searchTitle = it })
                Text("搜索标题")
            }
            Text(
                if (keyword.isBlank()) "输入关键词后自动查找" else "共找到 $total 处",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(hits) { index, hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = index }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            "${hit.chapter.title}（${hit.count} 处）",
                            fontWeight = if (index == safeSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == safeSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                        Text(
                            hit.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}