package com.example.novelreader.presentation.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.novelreader.domain.model.ChatMessage
import com.example.novelreader.domain.model.CommunityComment
import com.example.novelreader.domain.model.Prompt
import com.example.novelreader.domain.model.SharedNovel
import com.example.novelreader.presentation.viewmodel.CommunityViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import com.example.novelreader.presentation.viewmodel.CommunityActionState

// ============================================================
// COMMUNITY SCREEN — Chat / Chia sẻ truyện / Diễn đàn Prompt
// Matches screenshot: 4 bottom tabs inside this screen
// ============================================================

@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel = hiltViewModel()) {
    val tabs = listOf("Chat", "Chia sẻ truyện", "Diễn đàn")
    var selectedTab by remember { mutableStateOf(0) }
    val actionState by viewModel.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Hiện snackbar khi có kết quả
    LaunchedEffect(actionState) {                                   // <-- thêm block này
        when (actionState) {
            is CommunityActionState.Success ->
                snackbarHostState.showSnackbar((actionState as CommunityActionState.Success).message)
            is CommunityActionState.Error ->
                snackbarHostState.showSnackbar((actionState as CommunityActionState.Error).message)
            else -> {}
        }
        if (actionState !is CommunityActionState.Idle) {
            viewModel.clearActionState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ChatTab(viewModel = viewModel)
                1 -> SharedNovelsTab(viewModel)
                2 -> PromptForumTab(viewModel)
            }
        }
    }
}

// ============================================================
// CHAT TAB
// ============================================================

@Composable
fun ChatTab(viewModel: CommunityViewModel) {
    val messages by viewModel.messages.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Forum, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Chat cộng đồng", fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    CommunityEmptyState(
                        icon = Icons.Default.Forum,
                        title = "Chưa có tin nhắn",
                        message = "Tin nhắn cộng đồng sẽ xuất hiện ở đây."
                    )
                }
            } else {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message, isOwn = message.userId == viewModel.currentUserId)
                }
            }
        }

        // Input row
        if (viewModel.isLoggedIn) {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Nhập tin nhắn...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.sendMessage(input)
                                input = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, "Gửi", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "Đăng nhập để tham gia chat",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isOwn: Boolean) {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isOwn) {
            AsyncImage(
                model = message.userAvatar.ifBlank { null },
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
            if (!isOwn) {
                Text(message.userName, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary)
            }

            // Reply preview
            message.replyToContent?.let { quote ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    modifier = Modifier.widthIn(max = 240.dp)
                ) {
                    Text(
                        quote,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = if (isOwn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = if (isOwn) 12.dp else 4.dp,
                    topEnd = if (isOwn) 4.dp else 12.dp,
                    bottomStart = 12.dp, bottomEnd = 12.dp
                ),
                modifier = Modifier.widthIn(max = 260.dp)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                sdf.format(Date(message.timestamp)),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

// ============================================================
// SHARED NOVELS TAB
// ============================================================

@Composable
fun SharedNovelsTab(viewModel: CommunityViewModel) {
    val novels by viewModel.sharedNovels.collectAsState()
    val comments by viewModel.sharedNovelComments.collectAsState()
    val context = LocalContext.current
    var showTitleDialog by remember { mutableStateOf(false) }
    var selectedBytes by remember { mutableStateOf<ByteArray?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                selectedBytes = bytes
                showTitleDialog = true
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kho truyện chia sẻ", fontWeight = FontWeight.Bold)
                if (viewModel.isLoggedIn) {
                    FilledTonalButton(onClick = {
                        fileLauncher.launch(arrayOf("text/plain"))
                    }) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tải lên")
                    }
                }
            }
        }

        if (novels.isEmpty()) {
            item {
                CommunityEmptyState(
                    icon = Icons.Default.Article,
                    title = "Chưa có truyện chia sẻ",
                    message = if (viewModel.isLoggedIn) {
                        "Bạn có thể tải file TXT lên để chia sẻ cho mọi người."
                    } else {
                        "Đăng nhập để chia sẻ truyện TXT của bạn."
                    }
                )
            }
        } else {
            items(novels, key = { it.id }) { novel ->
                SharedNovelCard(
                    novel = novel,
                    comments = comments[novel.id].orEmpty(),
                    canComment = viewModel.isLoggedIn,
                    onDownload = { viewModel.downloadAndImport(novel) },
                    onComment = { viewModel.postSharedNovelComment(novel.id, it) }
                )
            }
        }
    }

    // Dialog nhập tên truyện trước khi upload
    if (showTitleDialog) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("Đặt tên truyện") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên truyện") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedBytes?.let { bytes ->
                            viewModel.shareNovel(
                                title.ifBlank { "Truyện không tên" },
                                bytes
                            )
                        }
                        showTitleDialog = false
                    }
                ) { Text("Tải lên") }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
fun SharedNovelCard(
    novel: SharedNovel,
    comments: List<CommunityComment>,
    canComment: Boolean,
    onDownload: () -> Unit,
    onComment: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Article, null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(novel.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("bởi ${novel.uploaderName} • ${novel.fileSize / 1024}KB",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${novel.downloadCount} lượt tải", fontSize = 11.sp)
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, "Tải về")
                }
            }
            CommentSection(
                comments = comments,
                canComment = canComment,
                onPost = onComment
            )
        }
    }
}

// ============================================================
// PROMPT FORUM TAB
// ============================================================

@Composable
fun PromptForumTab(viewModel: CommunityViewModel) {
    val prompts by viewModel.prompts.collectAsState()
    val comments by viewModel.promptComments.collectAsState()
    var showPostDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Diễn đàn Prompt dịch", fontWeight = FontWeight.Bold)
                if (viewModel.isLoggedIn) {
                    FilledTonalButton(onClick = { showPostDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đăng prompt")
                    }
                }
            }
        }

        if (prompts.isEmpty()) {
            item {
                CommunityEmptyState(
                    icon = Icons.Default.Lightbulb,
                    title = "Chưa có prompt",
                    message = if (viewModel.isLoggedIn) {
                        "Đăng prompt dịch hay để mọi người cùng dùng."
                    } else {
                        "Đăng nhập để đăng và đánh giá prompt."
                    }
                )
            }
        } else {
            items(prompts, key = { it.id }) { prompt ->
                PromptCard(
                    prompt = prompt,
                    canLike = viewModel.isLoggedIn,
                    comments = comments[prompt.id].orEmpty(),
                    canComment = viewModel.isLoggedIn,
                    onLike = { viewModel.likePrompt(prompt.id) },
                    onComment = { viewModel.postPromptComment(prompt.id, it) }
                )
            }
        }
    }

    if (showPostDialog) {
        PostPromptDialog(
            onPost = { title, content ->
                viewModel.postPrompt(title, content)
                showPostDialog = false
            },
            onDismiss = { showPostDialog = false }
        )
    }
}

@Composable
fun PromptCard(
    prompt: Prompt,
    canLike: Boolean,
    comments: List<CommunityComment>,
    canComment: Boolean,
    onLike: () -> Unit,
    onComment: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(prompt.title, fontWeight = FontWeight.Bold)
            Text("bởi ${prompt.authorName}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                text = prompt.content,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Thu gọn" else "Xem thêm")
                }
                Row {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(prompt.content))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Sao chép", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onLike, enabled = canLike) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ThumbUp, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("${prompt.likes}", fontSize = 12.sp)
                        }
                    }
                }
            }
            CommentSection(
                comments = comments,
                canComment = canComment,
                onPost = onComment
            )
        }
    }
}

@Composable
fun CommentSection(
    comments: List<CommunityComment>,
    canComment: Boolean,
    onPost: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }

    HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
    Text(
        "Bình luận (${comments.size})",
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    )
    Spacer(Modifier.height(6.dp))

    comments.forEach { comment ->
        CommentItem(comment)
        Spacer(Modifier.height(6.dp))
    }

    if (canComment) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Viết bình luận...") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = RoundedCornerShape(18.dp)
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        onPost(input)
                        input = ""
                    }
                }
            ) {
                Icon(Icons.Default.Send, "Gửi bình luận", tint = MaterialTheme.colorScheme.primary)
            }
        }
    } else {
        Text(
            "Đăng nhập để bình luận.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CommentItem(comment: CommunityComment) {
    Row(verticalAlignment = Alignment.Top) {
        AsyncImage(
            model = comment.userAvatar.ifBlank { null },
            contentDescription = comment.userName,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(comment.userName, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(comment.content, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun CommunityEmptyState(
    icon: ImageVector,
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PostPromptDialog(onPost: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đăng Prompt mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Tiêu đề") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = content, onValueChange = { content = it },
                    label = { Text("Nội dung prompt") }, modifier = Modifier.fillMaxWidth(),
                    minLines = 5, maxLines = 10)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPost(title, content) },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) { Text("Đăng") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@Composable
fun FeedbackTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Góp ý & Báo lỗi\n(Liên kết đến form phản hồi)", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
