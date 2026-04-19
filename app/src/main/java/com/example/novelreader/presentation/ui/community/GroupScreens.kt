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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.novelreader.domain.model.ChatGroup
import com.example.novelreader.domain.model.ChatMessage
import com.example.novelreader.presentation.viewmodel.GroupUiState
import com.example.novelreader.presentation.viewmodel.GroupViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.produceState
import com.google.firebase.Firebase
import com.google.firebase.database.database
import kotlinx.coroutines.tasks.await

// ============================================================
// GROUP LIST SCREEN
// ============================================================

@Composable
fun GroupListScreen(
    onOpenGroup: (ChatGroup) -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is GroupUiState.Success -> snackbarHostState.showSnackbar((uiState as GroupUiState.Success).message)
            is GroupUiState.Error -> snackbarHostState.showSnackbar((uiState as GroupUiState.Error).message)
            else -> {}
        }
        if (uiState !is GroupUiState.Idle) viewModel.clearUiState()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Nhóm chat", fontWeight = FontWeight.Bold) },
                actions = {
                    if (viewModel.isLoggedIn) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, "Tạo nhóm")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Forum, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("Chưa có nhóm nào",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (viewModel.isLoggedIn) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showCreateDialog = true }) {
                            Text("Tạo nhóm đầu tiên")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        currentUserId = viewModel.currentUserId,
                        onClick = {
                            viewModel.openGroup(group)
                            onOpenGroup(group)
                        },
                        onJoin = { viewModel.joinGroup(group.id) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onConfirm = { name, desc ->
                viewModel.createGroup(name, desc)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
fun GroupCard(
    group: ChatGroup,
    currentUserId: String?,
    onClick: () -> Unit,
    onJoin: () -> Unit
) {
    val isMember = currentUserId != null && currentUserId in group.members

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    group.name.firstOrNull()?.uppercase() ?: "G",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (group.description.isNotBlank()) {
                    Text(group.description, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${group.members.size} thành viên",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isMember && currentUserId != null) {
                FilledTonalButton(onClick = onJoin) { Text("Tham gia", fontSize = 12.sp) }
            } else if (isMember) {
                Icon(Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun CreateGroupDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo nhóm mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Tên nhóm *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Mô tả (tùy chọn)") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, desc) }, enabled = name.isNotBlank()) {
                Text("Tạo")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

// ============================================================
// GROUP CHAT SCREEN
// ============================================================

@Composable
fun GroupChatScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenSettings: (String) -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val currentGroup by viewModel.currentGroup.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is GroupUiState.Error -> snackbarHostState.showSnackbar((uiState as GroupUiState.Error).message)
            else -> {}
        }
        if (uiState !is GroupUiState.Idle) viewModel.clearUiState()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(currentGroup?.name ?: "Nhóm chat", fontWeight = FontWeight.Bold)
                        Text("${currentGroup?.members?.size ?: 0} thành viên",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Quay lại") }
                },
                actions = {
                    IconButton(onClick = { onOpenSettings(groupId) }) {
                        Icon(Icons.Default.Settings, "Cài đặt nhóm")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    GroupChatBubble(
                        message = message,
                        isOwn = message.userId == viewModel.currentUserId,
                        isAdmin = currentGroup?.adminId == viewModel.currentUserId,
                        onDelete = { viewModel.deleteMessage(message.id) }
                    )
                }
            }

            if (viewModel.isLoggedIn) {
                Surface(shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = input, onValueChange = { input = it },
                            placeholder = { Text("Nhập tin nhắn...") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            if (input.isNotBlank()) {
                                viewModel.sendMessage(input)
                                input = ""
                            }
                        }) {
                            Icon(Icons.Default.Send, "Gửi", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("Đăng nhập để tham gia chat",
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun GroupChatBubble(
    message: ChatMessage,
    isOwn: Boolean,
    isAdmin: Boolean,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isOwn) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(message.userName.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
            if (!isOwn) {
                Text(message.userName, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isOwn) 16.dp else 4.dp,
                    topEnd = if (isOwn) 4.dp else 16.dp,
                    bottomStart = 16.dp, bottomEnd = 16.dp
                ),
                color = if (isOwn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(sdf.format(Date(message.timestamp)),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Nút xóa chỉ hiện với Admin
                if (isAdmin && !isOwn) {
                    IconButton(onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Delete, "Xóa",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Xóa tin nhắn?") },
            text = { Text("Tin nhắn này sẽ bị xóa vĩnh viễn.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Hủy") }
            }
        )
    }
}

// ============================================================
// GROUP SETTINGS SCREEN
// ============================================================

@Composable
fun GroupSettingsScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: GroupViewModel = hiltViewModel()
) {
    val currentGroup by viewModel.currentGroup.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isAdmin = currentGroup?.adminId == viewModel.currentUserId

    LaunchedEffect(uiState) {
        when (uiState) {
            is GroupUiState.Success -> snackbarHostState.showSnackbar((uiState as GroupUiState.Success).message)
            is GroupUiState.Error -> snackbarHostState.showSnackbar((uiState as GroupUiState.Error).message)
            else -> {}
        }
        if (uiState !is GroupUiState.Idle) viewModel.clearUiState()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt nhóm") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Quay lại") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Thành viên (${currentGroup?.members?.size ?: 0})",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            items(currentGroup?.members ?: emptyList()) { uid ->
                MemberRow(
                    uid = uid,
                    isAdmin = currentGroup?.adminId == uid,
                    canKick = isAdmin && uid != viewModel.currentUserId,
                    onKick = { viewModel.kickMember(uid) }
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.leaveGroup(groupId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rời nhóm")
                }
            }
        }
    }
}

@Composable
fun MemberRow(uid: String, isAdmin: Boolean, canKick: Boolean, onKick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            val displayName by produceState(initialValue = uid.take(8) + "...", uid) {
                runCatching {
                    val snap = Firebase.database.reference
                        .child("users").child(uid).get().await()
                    snap.child("name").getValue(String::class.java)
                }.getOrNull()?.let { value = it }
            }
            Text(displayName, fontSize = 13.sp)
            if (isAdmin) {
                Text("Admin", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium)
            }
        }
        if (canKick) {
            IconButton(onClick = onKick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.PersonRemove, "Kick",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}