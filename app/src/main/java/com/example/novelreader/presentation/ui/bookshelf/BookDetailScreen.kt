package com.example.novelreader.presentation.ui.bookshelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.novelreader.presentation.viewmodel.SaveState

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
    val saveState by viewModel.saveState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Form state — khởi tạo từ book khi book load xong
    var editTitle by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    // Sync form khi book load lần đầu
    LaunchedEffect(book) {
        book?.let {
            if (editTitle.isEmpty()) editTitle = it.title
            if (editAuthor.isEmpty()) editAuthor = it.author
        }
    }

    // Snackbar phản hồi
    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Saved -> snackbarHostState.showSnackbar("Đã lưu thông tin truyện")
            is SaveState.Error -> snackbarHostState.showSnackbar((saveState as SaveState.Error).message)
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Thông tin truyện") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Quay lại")
                    }
                },
                actions = {
                    if (isEditing) {
                        // Lưu
                        IconButton(onClick = {
                            viewModel.updateBookInfo(editTitle, editAuthor)
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Check, "Lưu")
                        }
                        // Hủy
                        IconButton(onClick = {
                            book?.let { editTitle = it.title; editAuthor = it.author }
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Close, "Hủy")
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, "Chỉnh sửa")
                        }
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
                // ---- Header: ảnh bìa + form thông tin ----
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Ảnh bìa
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
                            if (isEditing) {
                                // ---- Chế độ chỉnh sửa ----
                                OutlinedTextField(
                                    value = editTitle,
                                    onValueChange = { editTitle = it },
                                    label = { Text("Tên truyện") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    isError = editTitle.isBlank()
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editAuthor,
                                    onValueChange = { editAuthor = it },
                                    label = { Text("Tác giả") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Không rõ") }
                                )
                            } else {
                                // ---- Chế độ xem ----
                                Text(
                                    b.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tác giả: ${b.author.ifBlank { "Không rõ" }}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Số chương: ${chapters.size}",
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Nguồn: ${b.source}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

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

                // ---- Mô tả ----
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

                // ---- Danh sách chương ----
                item {
                    Text(
                        "Danh sách chương (${chapters.size})",
                        fontWeight = FontWeight.Bold
                    )
                }

                items(chapters, key = { it.id }) { chapter ->
                    ListItem(
                        headlineContent = {
                            Text(chapter.title, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text("Chương ${chapter.chapterIndex + 1}", fontSize = 11.sp)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (chapter.isBookmarked) {
                                    Icon(Icons.Default.Bookmark, null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (chapter.translatedContent.isNotBlank()) {
                                    Text("Đã dịch", fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        modifier = Modifier.clickable { onOpenReader(chapter.chapterIndex) }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}