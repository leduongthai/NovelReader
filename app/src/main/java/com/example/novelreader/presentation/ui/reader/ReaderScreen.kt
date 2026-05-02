package com.example.novelreader.presentation.ui.reader

import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.novelreader.domain.model.Chapter
import com.example.novelreader.domain.model.ReaderBackground
import com.example.novelreader.domain.model.ReaderSettings
import com.example.novelreader.presentation.viewmodel.ReaderUiState
import com.example.novelreader.presentation.viewmodel.ReaderViewModel
import com.example.novelreader.presentation.viewmodel.TranslationState
import java.util.Locale
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder

// ============================================================
// READER SCREEN — Full-featured novel reader
// Matches reference screenshot: sepia/paper background, large text
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    initialChapterIndex: Int,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val chapter by viewModel.currentChapter.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val readerState by viewModel.readerState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val translationState by viewModel.translationState.collectAsState()
    val configReady by viewModel.configReady.collectAsState()
    val context = LocalContext.current

    var showControls by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }

    // TTS engine
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsPlaying by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Background and text colors from settings
    val bg = Color(settings.backgroundColor.bg)
    val textColor = Color(settings.backgroundColor.text)

    // Load book on entry
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    // Open initial chapter only after BOTH config AND chapters are ready
    LaunchedEffect(configReady) {
        if (configReady && chapter == null) {
            val c = chapters.getOrNull(initialChapterIndex) ?: chapters.firstOrNull() ?: return@LaunchedEffect
            android.util.Log.d("Crawler", "ReaderScreen: opening chapter index=$initialChapterIndex title=${c.title}")
            viewModel.openChapter(c)
        }
    }

    // Initialize TTS
    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
                tts?.setSpeechRate(settings.ttsSpeed)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { tts?.shutdown() }
    }

    // Save progress periodically
    LaunchedEffect(listState.firstVisibleItemIndex) {
        chapter?.let { c ->
            val total = chapters.size.coerceAtLeast(1)
            val progress = (c.chapterIndex.toFloat() + listState.firstVisibleItemScrollOffset / 1000f) / total
            viewModel.saveProgress(bookId, c.id, c.chapterIndex, progress.coerceIn(0f, 1f))
        }
    }

    // Determine text to display
    val displayText = when {
        showTranslation && translationState is TranslationState.Done ->
            (translationState as TranslationState.Done).translation
        else -> chapter?.content ?: ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures { showControls = !showControls }
            }
    ) {
        when (readerState) {
            is ReaderUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is ReaderUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                    Text((readerState as ReaderUiState.Error).message, color = textColor)
                }
            }
            else -> {
                // ---- Main text content ----
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(vertical = 60.dp)
                ) {
                    item {
                        chapter?.let { c ->
                            Text(
                                text = c.title,
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = (settings.fontSize + 4).sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    fontFamily = getFontFamily(settings.fontFamily)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp)
                            )
                        }
                    }

                    // Render paragraphs individually for better performance
                    displayText.split("\n\n").filter { it.isNotBlank() }.forEachIndexed { _, para ->
                        item {
                            Text(
                                text = para.trim(),
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = settings.fontSize.sp,
                                    lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                                    fontFamily = getFontFamily(settings.fontFamily),
                                    textAlign = TextAlign.Justify
                                ),
                                modifier = Modifier.padding(bottom = (settings.fontSize * 0.6f).dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Top bar (shown/hidden on tap) ----
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            ReaderTopBar(
                chapterTitle = chapter?.title ?: "",
                isBookmarked = chapter?.isBookmarked ?: false,
                onBack = onBack,
                onChapterList = { showChapterList = true },
                onToggleBookmark = { viewModel.toggleBookmark() },
                onShare = {
                    val link = viewModel.getShareLink(bookId)
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, "Đọc truyện này cùng mình: $link")
                        type = "text/plain"
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, "Chia sẻ truyện"))
                }
            )
        }

        // ---- Bottom bar ----
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            ReaderBottomBar(
                currentChapterIndex = chapter?.chapterIndex ?: 0,
                totalChapters = chapters.size,
                isTranslating = translationState is TranslationState.Translating,
                showingTranslation = showTranslation,
                isTtsPlaying = isTtsPlaying,
                onPrevChapter = { viewModel.navigateChapter(-1) },
                onNextChapter = { viewModel.navigateChapter(1) },
                onSeek = { index ->
                    val c = chapters.getOrNull(index)
                    c?.let { viewModel.openChapter(it) }   // config resolved internally
                },
                onTranslate = {
                    viewModel.translateCurrentChapter()
                    if (translationState !is TranslationState.Idle) showTranslation = !showTranslation
                },
                onTts = {
                    if (isTtsPlaying) {
                        tts?.stop()
                        isTtsPlaying = false
                    } else {
                        tts?.speak(displayText, TextToSpeech.QUEUE_FLUSH, null, "chapter")
                        isTtsPlaying = true
                    }
                },
                onSettings = { showSettingsSheet = true }
            )
        }

        // Translation state indicator
        if (translationState is TranslationState.Translating) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
            )
        }
    }

    // ---- Settings Bottom Sheet ----
    if (showSettingsSheet) {
        ReaderSettingsSheet(
            settings = settings,
            onFontSizeChange = viewModel::updateFontSize,
            onFontFamilyChange = viewModel::updateFontFamily,
            onBackgroundChange = viewModel::updateBackground,
            onDarkModeChange = viewModel::updateDarkMode,
            onDismiss = { showSettingsSheet = false }
        )
    }

    // ---- Chapter List Modal ----
    if (showChapterList) {
        ChapterListModal(
            chapters = chapters,
            currentIndex = chapter?.chapterIndex ?: 0,
            onSelectChapter = { c ->
                viewModel.openChapter(c)   // config resolved internally
                showChapterList = false
            },
            onDismiss = { showChapterList = false }
        )
    }
}

// ---- Helper: font family mapping ----
fun getFontFamily(name: String): FontFamily = when (name) {
    "serif"     -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    else        -> FontFamily.Default
}

// ============================================================
// COMPOSABLE SUBCOMPONENTS
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(chapterTitle: String,
                 isBookmarked: Boolean,
                 onBack: () -> Unit,
                 onChapterList: () -> Unit,
                 onToggleBookmark: () -> Unit,
                 onShare: () -> Unit
) {
    TopAppBar(
        title = {
            Text(chapterTitle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Quay lại") }
        },
        actions = {
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Chia sẻ") }
            IconButton(onClick = onChapterList) { Icon(Icons.Default.List, "Danh sách chương") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    )
}

@Composable
fun ReaderBottomBar(
    currentChapterIndex: Int,
    totalChapters: Int,
    isTranslating: Boolean,
    showingTranslation: Boolean,
    isTtsPlaying: Boolean,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeek: (Int) -> Unit,
    onTranslate: () -> Unit,
    onTts: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Progress slider
            if (totalChapters > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onPrevChapter, contentPadding = PaddingValues(4.dp)) {
                        Text("Trước", fontSize = 12.sp)
                    }
                    Slider(
                        value = currentChapterIndex.toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..(totalChapters - 1).toFloat().coerceAtLeast(0f),
                        steps = (totalChapters - 2).coerceAtLeast(0),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onNextChapter, contentPadding = PaddingValues(4.dp)) {
                        Text("Tiếp", fontSize = 12.sp)
                    }
                }
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Chapter list
                IconButton(onClick = {}) {
                    Icon(Icons.Default.FormatListBulleted, "Danh sách")
                }
                // TTS
                IconButton(onClick = onTts) {
                    Icon(
                        if (isTtsPlaying) Icons.Default.StopCircle else Icons.Default.Headphones,
                        "Đọc truyện"
                    )
                }
                // AI Translate
                IconButton(onClick = onTranslate, enabled = !isTranslating) {
                    if (isTranslating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            Icons.Default.Translate,
                            "Dịch AI",
                            tint = if (showingTranslation) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current
                        )
                    }
                }
                // Settings
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, "Cài đặt")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onFontSizeChange: (Float) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onBackgroundChange: (ReaderBackground) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cài đặt đọc", style = MaterialTheme.typography.titleMedium)

            // Font size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cỡ chữ", modifier = Modifier.width(80.dp))
                IconButton(onClick = { onFontSizeChange((settings.fontSize - 1).coerceAtLeast(12f)) }) {
                    Icon(Icons.Default.Remove, null)
                }
                Text("${settings.fontSize.toInt()}pt", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { onFontSizeChange((settings.fontSize + 1).coerceAtMost(36f)) }) {
                    Icon(Icons.Default.Add, null)
                }
            }

            // Font family
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Font", modifier = Modifier.width(80.dp))
                listOf("default" to "Mặc định", "serif" to "Serif", "monospace" to "Mono").forEach { (key, label) ->
                    FilterChip(
                        selected = settings.fontFamily == key,
                        onClick = { onFontFamilyChange(key) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Background
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nền", modifier = Modifier.width(80.dp))
                ReaderBackground.entries.forEach { bg ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(bg.bg))
                            .border(
                                width = if (settings.backgroundColor == bg) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onBackgroundChange(bg) }
                    )
                }
            }

            // Dark mode
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Chế độ tối", modifier = Modifier.weight(1f))
                Switch(checked = settings.isDarkMode, onCheckedChange = onDarkModeChange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListModal(
    chapters: List<Chapter>,
    currentIndex: Int,
    onSelectChapter: (Chapter) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Text(
                    "Danh sách chương",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(chapters.size) { index ->
                val c = chapters[index]
                ListItem(
                    headlineContent = { Text(c.title, maxLines = 1) },
                    supportingContent = { Text("Chương ${c.chapterIndex + 1}", fontSize = 11.sp) },
                    colors = if (c.chapterIndex == currentIndex)
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else ListItemDefaults.colors(),
                    modifier = Modifier.clickable { onSelectChapter(c) }
                )
            }
        }
    }
}