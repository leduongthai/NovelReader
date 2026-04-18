package com.example.novelreader.presentation.ui.bookshelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.novelreader.domain.model.Book
import com.example.novelreader.domain.model.ReadingHistory
import com.example.novelreader.presentation.viewmodel.BookshelfViewModel
import com.example.novelreader.presentation.viewmodel.BookshelfUiState
import kotlin.math.roundToInt

// ============================================================
// BOOKSHELF SCREEN — "Kệ sách"
// Matches design from screenshot: 2-col grid + last-read banner
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    onOpenReader: (bookId: String, chapterIndex: Int) -> Unit,
    viewModel: BookshelfViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showImportDialog by remember { mutableStateOf(false) }
    var importTitle by remember { mutableStateOf("") }
    var importContent by remember { mutableStateOf("") }
    var customRegex by remember { mutableStateOf("") }

    // File picker launcher for TXT import
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)
                ?.bufferedReader()?.readText() ?: return@let
            importContent = text
            showImportDialog = true
        }
    }

    // Find most recently read book for banner
    val lastRead = progressMap.values.maxByOrNull { it.lastReadAt }
    val lastReadBook = lastRead?.let { h -> books.firstOrNull { it.id == h.bookId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kệ sách", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, "Tìm kiếm")
                    }
                    IconButton(onClick = { fileLauncher.launch(arrayOf("text/plain")) }) {
                        Icon(Icons.Default.Add, "Thêm truyện TXT")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Last-read banner (spans all columns)
            lastReadBook?.let { book ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LastReadBanner(
                        book = book,
                        history = lastRead!!,
                        onClick = {
                            onOpenReader(book.id, lastRead.chapterIndex)
                        }
                    )
                }
            }

            // Grid items
            items(books, key = { it.id }) { book ->
                val history = progressMap[book.id]
                BookGridItem(
                    book = book,
                    history = history,
                    onClick = { onOpenReader(book.id, history?.chapterIndex ?: 0) },
                    onDelete = { viewModel.deleteBook(book.id) }
                )
            }
        }

        // TXT import dialog
        if (showImportDialog) {
            ImportTxtDialog(
                defaultTitle = importTitle,
                onConfirm = { title, regex ->
                    viewModel.importTxtFile(importContent, title, regex.ifBlank { null })
                    showImportDialog = false
                },
                onDismiss = { showImportDialog = false }
            )
        }

        // State snackbar
        LaunchedEffect(uiState) {
            if (uiState is BookshelfUiState.ImportSuccess) {
                // show success toast — handled by Snackbar in production
            }
        }
    }
}

// ---- Last Read Banner ----

@Composable
fun LastReadBanner(book: Book, history: ReadingHistory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.height(110.dp)) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
            )
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Chương ${history.chapterIndex + 1}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Progress bar
                    LinearProgressIndicator(
                        progress = { history.scrollPosition },
                        modifier = Modifier.weight(1f).height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${(history.scrollPosition * 100).roundToInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ---- Grid Item ----

@Composable
fun BookGridItem(
    book: Book,
    history: ReadingHistory?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val progress = history?.scrollPosition ?: 0f

    Column(
        modifier = Modifier
            .clickable { onClick() }
    ) {
        Box {
            // Cover image
            AsyncImage(
                model = book.coverUrl.ifBlank { null },
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Progress badge
            if (progress > 0f) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 3-dot menu
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Xóa truyện") },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Thông tin truyện") },
                        onClick = { /* Navigate to info */ showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Info, null) }
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = book.title,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
        history?.let {
            Text(
                text = "C.${it.chapterIndex + 1}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Import TXT Dialog ----

@Composable
fun ImportTxtDialog(
    defaultTitle: String,
    onConfirm: (title: String, regex: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var regex by remember { mutableStateOf("") }
    var showRegexField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhập truyện TXT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên truyện") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(onClick = { showRegexField = !showRegexField }) {
                    Text(if (showRegexField) "Ẩn tùy chỉnh Regex" else "Tùy chỉnh Regex chia chương")
                }

                if (showRegexField) {
                    OutlinedTextField(
                        value = regex,
                        onValueChange = { regex = it },
                        label = { Text("Regex (để trống = mặc định)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("^(?:Chương|Chapter)\\s*\\d+.*$", fontSize = 11.sp) },
                        minLines = 2
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.ifBlank { "Truyện không tên" }, regex) },
                enabled = true
            ) { Text("Nhập") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
