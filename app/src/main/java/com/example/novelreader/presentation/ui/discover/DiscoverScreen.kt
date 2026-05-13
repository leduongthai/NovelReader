package com.example.novelreader.presentation.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.novelreader.data.remote.crawler.DiscoverFeed
import com.example.novelreader.domain.model.Book
import com.example.novelreader.domain.model.Chapter
import com.example.novelreader.presentation.viewmodel.DetailActionState
import com.example.novelreader.presentation.viewmodel.DetailUiState
import com.example.novelreader.presentation.viewmodel.DiscoverUiState
import com.example.novelreader.presentation.viewmodel.DiscoverViewModel
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.example.novelreader.domain.model.Review
import com.example.novelreader.presentation.viewmodel.ReviewUiState
import com.example.novelreader.presentation.viewmodel.ReviewViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

// ============================================================
// DISCOVER SCREEN — "Khám phá"
// ============================================================

@Composable
fun DiscoverScreen(
    onOpenDetail: (url: String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val novels by viewModel.filteredNovels.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFeed by viewModel.selectedFeed.collectAsState()

    LaunchedEffect(Unit) {
        if (novels.isEmpty()) viewModel.loadNovels()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Khám phá", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Thanh tìm kiếm
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = { Text("Tìm tên truyện, tác giả...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Xóa")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.searchRemote(searchQuery) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DiscoverFeed.entries) { feed ->
                    FilterChip(
                        selected = selectedFeed == feed,
                        onClick = { viewModel.selectFeed(feed) },
                        label = { Text(feed.title) },
                        leadingIcon = if (selectedFeed == feed) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Nội dung
            when (uiState) {
                is DiscoverUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is DiscoverUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp))
                            Text((uiState as DiscoverUiState.Error).message)
                            Button(onClick = { viewModel.loadNovels() }) { Text("Thử lại") }
                        }
                    }
                }
                else -> {
                    if (novels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (searchQuery.isNotBlank()) "Không tìm thấy \"$searchQuery\""
                                    else "Không có dữ liệu. Kiểm tra kết nối mạng.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (searchQuery.isBlank()) {
                                    Button(onClick = { viewModel.loadNovels() }) { Text("Thử lại") }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(
                                start = 12.dp, end = 12.dp,
                                top = 8.dp,
                                bottom = 8.dp
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
    onOpenReader: (bookId: String, chapterIndex: Int) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val detailState by viewModel.detailState.collectAsState()
    val detailActionState by viewModel.detailActionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var openAfterAddChapter by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(detailUrl) {
        viewModel.loadNovelDetail(detailUrl)
    }

    LaunchedEffect(detailActionState) {
        when (val state = detailActionState) {
            is DetailActionState.Added -> {
                val chapterIndex = openAfterAddChapter
                if (chapterIndex != null) {
                    onOpenReader(state.book.id, chapterIndex)
                } else {
                    val message = if (state.alreadyInShelf) {
                        "Truyện đã có trong kệ sách"
                    } else {
                        "Đã thêm \"${state.book.title}\" vào kệ sách"
                    }
                    snackbarHostState.showSnackbar(message)
                }
                openAfterAddChapter = null
                viewModel.clearDetailActionState()
            }
            is DetailActionState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                openAfterAddChapter = null
                viewModel.clearDetailActionState()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    isSaving = detailActionState is DetailActionState.Loading,
                    onAddToBookshelf = {
                        openAfterAddChapter = null
                        viewModel.addToBookshelf(detailUrl)
                    },
                    onReadNow = {
                        openAfterAddChapter = 0
                        viewModel.addToBookshelf(detailUrl)
                    },
                    onOpenReader = { chapterIndex ->
                        openAfterAddChapter = chapterIndex
                        viewModel.addToBookshelf(detailUrl)
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
    isSaving: Boolean,
    onAddToBookshelf: () -> Unit,
    onReadNow: () -> Unit,
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
                    Button(
                        onClick = onAddToBookshelf,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LibraryAdd, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Thêm vào kệ")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onReadNow,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Đọc ngay")
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

        item { Spacer(Modifier.height(12.dp)) }

        item {
            ReviewSection(
                bookKey = book.sourceUrl.ifBlank { book.id },
                bookId = book.id,
                sourceUrl = book.sourceUrl
            )
        }

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
@Composable
fun ReviewSection(
    bookKey: String,
    bookId: String = "",
    sourceUrl: String = "",
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val reviews by viewModel.reviews.collectAsState()
    val myReview by viewModel.myReview.collectAsState()
    val averageRating by viewModel.averageRating.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bookKey) { viewModel.loadReviews(bookKey, bookId, sourceUrl) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ReviewUiState.Success -> snackbarHostState.showSnackbar((uiState as ReviewUiState.Success).message)
            is ReviewUiState.Error -> snackbarHostState.showSnackbar((uiState as ReviewUiState.Error).message)
            else -> {}
        }
        if (uiState !is ReviewUiState.Idle) viewModel.clearUiState()
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Đánh giá", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (reviews.isNotEmpty()) {
                StarRatingDisplay(rating = averageRating, starSize = 14.dp)
                Text("%.1f (%d)".format(averageRating, reviews.size),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (viewModel.isLoggedIn) {
            ReviewInputCard(
                myReview = myReview,
                isLoading = uiState is ReviewUiState.Loading,
                onSubmit = { rating, comment -> viewModel.submitReview(rating, comment) }
            )
            Spacer(Modifier.height(10.dp))
        } else {
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("Đăng nhập để viết đánh giá",
                    modifier = Modifier.padding(12.dp), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (reviews.isEmpty()) {
            Text("Chưa có đánh giá nào. Hãy là người đầu tiên!",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
        } else {
            reviews.forEach { review ->
                ReviewCard(review = review)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun ReviewInputCard(myReview: Review?, isLoading: Boolean, onSubmit: (Int, String) -> Unit) {
    var selectedRating by remember(myReview) { mutableStateOf(myReview?.rating ?: 0) }
    var comment by remember(myReview) { mutableStateOf(myReview?.comment ?: "") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(if (myReview != null) "Đánh giá của bạn" else "Viết đánh giá",
                fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            StarRatingSelector(currentRating = selectedRating, onRatingChange = { selectedRating = it })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = comment, onValueChange = { comment = it },
                placeholder = { Text("Nhận xét của bạn (tùy chọn)...") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSubmit(selectedRating, comment) },
                enabled = !isLoading && selectedRating > 0,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (myReview != null) "Cập nhật" else "Gửi đánh giá")
            }
        }
    }
}

@Composable
fun StarRatingSelector(currentRating: Int, onRatingChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        (1..5).forEach { star ->
            IconButton(onClick = { onRatingChange(star) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (star <= currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "$star sao",
                    tint = if (star <= currentRating) androidx.compose.ui.graphics.Color(0xFFFFC107)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        if (currentRating > 0) {
            Text(
                text = when (currentRating) { 1->"Tệ"; 2->"Không hay"; 3->"Bình thường"; 4->"Hay"; else->"Tuyệt vời!" },
                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun StarRatingDisplay(rating: Float, starSize: androidx.compose.ui.unit.Dp = 16.dp) {
    Row {
        (1..5).forEach { star ->
            Icon(
                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFFFFC107),
                modifier = Modifier.size(starSize)
            )
        }
    }
}

@Composable
fun ReviewCard(review: Review) {
    val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(review.userName.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(review.userName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(sdf.format(java.util.Date(review.createdAt)),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StarRatingDisplay(rating = review.rating.toFloat(), starSize = 14.dp)
            }
            if (review.comment.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(review.comment, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}
