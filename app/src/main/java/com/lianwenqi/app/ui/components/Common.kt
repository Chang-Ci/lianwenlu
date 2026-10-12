package com.lianwenqi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lianwenqi.app.data.entity.Book
import java.io.File

/** 默认封面配色 */
val CoverColors = listOf(
    0xFF3F51B5.toInt(), 0xFF00796B.toInt(), 0xFFC2185B.toInt(), 0xFF5D4037.toInt(),
    0xFF455A64.toInt(), 0xFF7B1FA2.toInt(), 0xFFEF6C00.toInt(), 0xFF2E7D32.toInt()
)

/** 书籍封面：有图片用图片，否则纯色 + 书名 */
@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    titleSize: Int = 15
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(book.coverColor)),
        contentAlignment = Alignment.Center
    ) {
        if (!book.coverPath.isNullOrBlank()) {
            AsyncImage(
                model = File(book.coverPath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = book.title,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = titleSize.sp),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}