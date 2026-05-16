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
import androidx.compose.runtime.saveable.rememberSaveable
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
    val autoOpenLastBook by viewModel.autoOpenLastBook.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTopMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportTitle by remember { mutableStateOf("") }
    var pendingImportText by remember { mutableStateOf("") }
    var autoOpenedLastBook by rememberSaveable { mutableStateOf(false) }

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
            pendingImportTitle = fileName
            pendingImportText = text
            showImportDialog = true
        }
    }

    val sortedBooks = remember(books, progressMap) {
        books.sortedWith(
            compareByDescending<Book> { progressMap[it.id]?.lastReadAt ?: 0L }
                .thenByDescending { it.addedAt }
        )
    }
    val lastRead = progressMap.values.maxByOrNull { it.lastReadAt }
    val lastReadBook = lastRead?.let { h -> books.firstOrNull { it.id == h.bookId } }
    val shelfBooks = remember(sortedBooks, lastReadBook?.id) {
        sortedBooks.filterNot { it.id == lastReadBook?.id }
    }

    LaunchedEffect(autoOpenLastBook, lastReadBook?.id, lastRead?.chapterIndex) {
        val book = lastReadBook
        val history = lastRead
        if (autoOpenLastBook && !autoOpenedLastBook && book != null && history != null) {
            autoOpenedLastBook = true
            onOpenReader(book.id, history.chapterIndex)
        }
    }

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
                    IconButton(onClick = { showTopMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Thêm truyện")
                    }
                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nhập file TXT") },
                            leadingIcon = { Icon(Icons.Default.Upload, null) },
                            onClick = {
                                showTopMenu = false
                                fileLauncher.launch(arrayOf("text/plain", "text/*", "*/*"))
                            }
                        )
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
                        onClick = { onOpenReader(book.id, lastRead.chapterIndex) },
                        onDelete = { viewModel.deleteBook(book.id) },
                        onOpenDetail = { onOpenDetail(book.id) }
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
                            Text("Nhấn nút ba chấm để nhập file TXT", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            items(shelfBooks, key = { it.id }) { book ->
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

    if (showImportDialog) {
        ImportTxtDialog(
            title = pendingImportTitle,
            onTitleChange = { pendingImportTitle = it },
            onImport = { customRegex ->
                viewModel.importTxtFile(
                    rawText = pendingImportText,
                    title = pendingImportTitle.ifBlank { "Truyện không tên" },
                    customRegex = customRegex
                )
                showImportDialog = false
                pendingImportText = ""
            },
            onDismiss = {
                showImportDialog = false
                pendingImportText = ""
            }
        )
    }
}

// ---- Last Read Banner ----

@Composable
fun LastReadBanner(
    book: Book,
    history: ReadingHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
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
            Box(modifier = Modifier.padding(top = 4.dp, end = 4.dp)) {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, "Tùy chọn truyện")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Thông tin truyện") },
                        onClick = {
                            showMenu = false
                            onOpenDetail()
                        },
                        leadingIcon = { Icon(Icons.Default.Info, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa truyện") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
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

@Composable
fun ImportTxtDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    onImport: (customRegex: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var useCustomRegex by remember { mutableStateOf(false) }
    var customRegex by remember {
        mutableStateOf("""^[ 　\t]{0,4}(?:[Cc]hương|[Cc]hapter|[Ss]ection|[Pp]art|[Pp]hần|[Nn][Oo]\.|[Ee]pisode)\s{0,4}\d{1,4}.{0,40}$""")
    }
    var regexError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhập truyện TXT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Tên truyện") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useCustomRegex,
                        onCheckedChange = {
                            useCustomRegex = it
                            regexError = null
                        }
                    )
                    Text("Tự nhập biểu thức chia chương")
                }
                if (useCustomRegex) {
                    OutlinedTextField(
                        value = customRegex,
                        onValueChange = {
                            customRegex = it
                            regexError = null
                        },
                        label = { Text("Regex tiêu đề chương") },
                        minLines = 3,
                        maxLines = 5,
                        isError = regexError != null,
                        supportingText = {
                            Text(regexError ?: "Để trống lựa chọn này nếu muốn dùng mẫu mặc định.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val regex = if (useCustomRegex) customRegex.trim().ifBlank { null } else null
                    if (regex != null && runCatching { Regex(regex, setOf(RegexOption.MULTILINE)) }.isFailure) {
                        regexError = "Regex không hợp lệ"
                    } else {
                        onImport(regex)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Nhập")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
