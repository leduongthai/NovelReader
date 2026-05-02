package com.example.novelreader.presentation.ui.bookshelf

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ============================================================
// BOOKSHELF SCREEN
// ============================================================

/** Lấy tên file từ URI, bỏ đuôi .txt */
fun Uri.getFileName(context: android.content.Context): String {
    context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1) {
                val name = cursor.getString(idx) ?: ""
                return name.removeSuffix(".txt").removeSuffix(".TXT")
                    .removeSuffix(".Txt").trim().ifBlank { "Truyện không tên" }
            }
        }
    }
    return lastPathSegment?.removeSuffix(".txt")?.removeSuffix(".TXT")
        ?.trim()?.ifBlank { "Truyện không tên" } ?: "Truyện không tên"
}

/**
 * Đọc TXT với auto-detect encoding.
 * Thứ tự: UTF-8 (bỏ BOM) → Windows-1258 → ISO-8859-1
 */
fun readTxtFile(context: android.content.Context, uri: Uri): String? {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null

        // Thử UTF-8 — phổ biến nhất
        val utf8 = try {
            val s = String(bytes, Charsets.UTF_8)
            // Nếu >1% ký tự bị thay thế → sai encoding
            if (s.count { it == '\uFFFD' } > s.length / 100) null else s
        } catch (e: Exception) { null }

        // Fallback Windows-1258 → Latin-1
        val raw = utf8 ?: try {
            String(bytes, charset("windows-1258"))
        } catch (e: Exception) {
            String(bytes, Charsets.ISO_8859_1)
        }

        // Bỏ UTF-8 BOM nếu có
        raw.trimStart('\uFEFF')
    } catch (e: Exception) { null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    onOpenReader: (bookId: String, chapterIndex: Int) -> Unit,
    onOpenDetail: (bookId: String) -> Unit,
    viewModel: BookshelfViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Chọn file → đọc trên IO thread → import
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val fileName = uri.getFileName(context)
        scope.launch {
            // Đọc file trên IO thread, KHÔNG block UI
            val text = withContext(Dispatchers.IO) { readTxtFile(context, uri) }
            if (text == null || text.isBlank()) {
                snackbarHostState.showSnackbar("Không đọc được file. Thử file TXT khác.")
                return@launch
            }
            viewModel.importTxtFile(text, fileName, customRegex = null)
        }
    }

    val lastRead = progressMap.values.maxByOrNull { it.lastReadAt }
    val lastReadBook = lastRead?.let { h -> books.firstOrNull { it.id == h.bookId } }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is BookshelfUiState.ImportSuccess ->
                snackbarHostState.showSnackbar("✓ Đã nhập \"${s.book.title}\" — ${s.book.totalChapters} chương")
            is BookshelfUiState.Error ->
                snackbarHostState.showSnackbar("Lỗi: ${s.message}")
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Kệ sách", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        fileLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                    }) {
                        Icon(Icons.Default.Add, "Nhập file TXT")
                    }
                }
            )
        }
    ) { padding ->

        if (uiState is BookshelfUiState.Loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Đang xử lý file...", fontSize = 14.sp)
                }
            }
            return@Scaffold
        }

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
            lastReadBook?.let { book ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LastReadBanner(
                        book = book,
                        history = lastRead!!,
                        onClick = { onOpenReader(book.id, lastRead.chapterIndex) }
                    )
                }
            }

            if (books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MenuBook, null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("Kệ sách trống", fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Nhấn + để nhập file TXT", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            items(books, key = { it.id }) { book ->
                val history = progressMap[book.id]
                BookGridItem(
                    book = book,
                    history = history,
                    onClick = { onOpenReader(book.id, history?.chapterIndex ?: 0) },
                    onDelete = { viewModel.deleteBook(book.id) },
                    onOpenDetail = { onOpenDetail(book.id) }
                )
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
                modifier = Modifier.width(80.dp).fillMaxHeight()
            )
            Column(
                modifier = Modifier.padding(12.dp).weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(book.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Chương ${history.chapterIndex + 1}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { history.scrollPosition },
                        modifier = Modifier.weight(1f).height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text("${(history.scrollPosition * 100).roundToInt()}%",
                        fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ---- Book Grid Item ----

@Composable
fun BookGridItem(
    book: Book,
    history: ReadingHistory?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val progress = history?.scrollPosition ?: 0f

    Column(modifier = Modifier.clickable { onClick() }) {
        Box {
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
            if (progress > 0f) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Text("${(progress * 100).roundToInt()}%",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
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
                        onClick = { onOpenDetail(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Info, null) }
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(book.title, fontSize = 11.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        history?.let {
            Text("C.${it.chapterIndex + 1}", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
