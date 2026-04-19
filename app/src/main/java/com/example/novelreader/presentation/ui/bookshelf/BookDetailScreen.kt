package com.example.novelreader.presentation.ui.bookshelf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.novelreader.presentation.viewmodel.BookDetailViewModel
import androidx.compose.foundation.clickable
import com.example.novelreader.presentation.viewmodel.CommunityViewModel
import com.example.novelreader.presentation.viewmodel.CommunityActionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenReader: (chapterIndex: Int) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsState()
    val chapters by viewModel.chapters.collectAsState()

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thông tin truyện") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        book?.let { b ->
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: ảnh bìa + thông tin
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AsyncImage(
                            model = b.coverUrl.ifBlank { null },
                            contentDescription = b.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(b.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Tác giả: ${b.author.ifBlank { "Không rõ" }}", fontSize = 13.sp)
                            Text("Số chương: ${chapters.size}", fontSize = 13.sp)
                            Text(
                                "Nguồn: ${b.source}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { onOpenReader(0) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Đọc từ đầu")
                            }
                        }
                    }
                }

                // Mô tả
                if (b.description.isNotBlank()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Giới thiệu", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(b.description, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }

                // Danh sách chương
                item {
                    Text(
                        "Danh sách chương (${chapters.size})",
                        fontWeight = FontWeight.Bold
                    )
                }

                items(chapters, key = { it.id }) { chapter ->
                    ListItem(
                        headlineContent = {
                            Text(chapter.title, fontSize = 13.sp, maxLines = 1)
                        },
                        supportingContent = {
                            Text("Chương ${chapter.chapterIndex + 1}", fontSize = 11.sp)
                        },
                        trailingContent = {
                            if (chapter.translatedContent.isNotBlank()) {
                                Text("Đã dịch", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable {
                            onOpenReader(chapter.chapterIndex)
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        } ?: Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}