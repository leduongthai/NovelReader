package com.example.novelreader.presentation.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.novelreader.domain.model.Book
import com.example.novelreader.domain.model.Chapter
import com.example.novelreader.presentation.viewmodel.DetailUiState
import com.example.novelreader.presentation.viewmodel.DiscoverUiState
import com.example.novelreader.presentation.viewmodel.DiscoverViewModel

// ============================================================
// DISCOVER SCREEN — "Khám phá"
// ============================================================

@Composable
fun DiscoverScreen(
    onOpenDetail: (url: String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val novels by viewModel.novels.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (novels.isEmpty()) viewModel.loadNovels()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Khám phá", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        when (uiState) {
            is DiscoverUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DiscoverUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp))
                        Text((uiState as DiscoverUiState.Error).message)
                        Button(onClick = { viewModel.loadNovels() }) { Text("Thử lại") }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(novels, key = { it.id }) { book ->
                        DiscoverBookCard(book = book, onClick = {
                            onOpenDetail(book.sourceUrl)
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverBookCard(book: Book, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            AsyncImage(
                model = book.coverUrl.ifBlank { null },
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    book.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
                Text(
                    book.author,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ============================================================
// NOVEL DETAIL SCREEN
// ============================================================

@Composable
fun NovelDetailScreen(
    detailUrl: String,
    onBack: () -> Unit,
    onOpenReader: (bookId: String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val detailState by viewModel.detailState.collectAsState()

    LaunchedEffect(detailUrl) {
        viewModel.loadNovelDetail(detailUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết truyện") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Quay lại") }
                }
            )
        }
    ) { padding ->
        when (val state = detailState) {
            is DetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.message)
                }
            }
            is DetailUiState.Success -> {
                NovelDetailContent(
                    book = state.book,
                    chapters = state.chapters,
                    modifier = Modifier.padding(padding),
                    onAddToBookshelf = {
                        viewModel.addToBookshelf(detailUrl)
                    },
                    onOpenReader = { chapterIndex ->
                        onOpenReader(state.book.id)
                    }
                )
            }
            else -> {}
        }
    }
}

@Composable
fun NovelDetailContent(
    book: Book,
    chapters: List<Chapter>,
    modifier: Modifier = Modifier,
    onAddToBookshelf: () -> Unit,
    onOpenReader: (Int) -> Unit
) {
    var showAllChapters by remember { mutableStateOf(false) }
    val visibleChapters = if (showAllChapters) chapters else chapters.take(20)

    LazyColumn(modifier = modifier) {
        // Header: cover + info
        item {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = book.coverUrl.ifBlank { null },
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(book.author, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("${book.totalChapters} chương", fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddToBookshelf, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LibraryAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Thêm vào kệ sách")
                    }
                }
            }
        }

        // Description
        item {
            Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Giới thiệu", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(book.description, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Chapter list header
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Danh sách chương (${chapters.size})", fontWeight = FontWeight.Bold)
            }
        }

        // Chapter items
        items(visibleChapters, key = { it.id }) { chapter ->
            ListItem(
                headlineContent = { Text(chapter.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("Chương ${chapter.chapterIndex + 1}", fontSize = 11.sp) },
                modifier = Modifier.clickable { onOpenReader(chapter.chapterIndex) }
            )
            HorizontalDivider(thickness = 0.5.dp)
        }

        // Show more button
        if (chapters.size > 20) {
            item {
                TextButton(
                    onClick = { showAllChapters = !showAllChapters },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showAllChapters) "Thu gọn" else "Xem tất cả ${chapters.size} chương")
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
