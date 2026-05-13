package com.example.novelreader.presentation.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.novelreader.domain.model.*
import com.example.novelreader.presentation.viewmodel.*

// ============================================================
// PROFILE SCREEN
// ============================================================

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    adminViewModel: AdminViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val profileAction by viewModel.profileAction.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(profileAction) {
        when (profileAction) {
            is ProfileActionState.Success ->
                snackbarHostState.showSnackbar((profileAction as ProfileActionState.Success).message)
            is ProfileActionState.Error ->
                snackbarHostState.showSnackbar((profileAction as ProfileActionState.Error).message)
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cá nhân") },
                actions = {
                    if (viewModel.isLoggedIn) {
                        TextButton(onClick = { viewModel.signOut() }) { Text("Đăng xuất") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (viewModel.isLoggedIn) {
                LoggedInProfile(
                    user           = currentUser,
                    settings       = settings,
                    onSaveApiKey   = viewModel::saveApiKey,
                    onSavePrompt   = viewModel::saveTranslationPrompt,
                    onUpdateProfile = viewModel::updateProfile,
                    adminViewModel  = adminViewModel
                )
            } else {
                AuthSection(
                    authState      = authState,
                    onSignIn       = viewModel::signIn,
                    onSignUp       = viewModel::signUp,
                    onResetPassword = viewModel::resetPassword
                )
                AiSettingsSection(
                    currentApiKey = settings.geminiApiKey,
                    currentPrompt = settings.translationPrompt,
                    onSaveApiKey = viewModel::saveApiKey,
                    onSavePrompt = viewModel::saveTranslationPrompt
                )
            }

            Spacer(Modifier.height(16.dp))
            ReaderSettingsSection()
        }
    }
}

// ============================================================
// AUTH SECTION (Login / Register)
// ============================================================

@Composable
fun AuthSection(
    authState: AuthState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onResetPassword: (String) -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isSignUp) "Đăng ký tài khoản" else "Đăng nhập",
                fontWeight = FontWeight.Bold, fontSize = 18.sp
            )

            if (isSignUp) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Tên hiển thị") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Mật khẩu") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (authState is AuthState.Error) {
                Text((authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    if (isSignUp) onSignUp(name, email, password)
                    else onSignIn(email, password)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                else Text(if (isSignUp) "Đăng ký" else "Đăng nhập")
            }

            if (!isSignUp) {
                if (authState is AuthState.PasswordResetSent) {
                    Text("✓ Đã gửi email đặt lại mật khẩu.",
                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                TextButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Quên mật khẩu?", fontSize = 13.sp) }
            }

            TextButton(
                onClick = { isSignUp = !isSignUp; name = ""; email = ""; password = "" },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (isSignUp) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký")
            }
        }
    }

    if (showResetDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Đặt lại mật khẩu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập email của bạn để nhận liên kết đặt lại.", fontSize = 14.sp)
                    OutlinedTextField(
                        value = resetEmail, onValueChange = { resetEmail = it },
                        label = { Text("Email") }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onResetPassword(resetEmail); showResetDialog = false }) { Text("Gửi") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Hủy") }
            }
        )
    }
}

// ============================================================
// LOGGED-IN PROFILE
// ============================================================

@Composable
fun LoggedInProfile(
    user: User?,
    settings: ReaderSettings,
    onSaveApiKey: (String) -> Unit,
    onSavePrompt: (String) -> Unit,
    onUpdateProfile: (String, String) -> Unit,
    adminViewModel: AdminViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // --- Header Avatar + Info ---
        UserHeader(user = user, onUpdateProfile = onUpdateProfile)

        HorizontalDivider()

        // --- Panel theo role ---
        when (user?.userRole) {
            UserRole.ADMIN -> AdminPanel(adminViewModel = adminViewModel)
            UserRole.MOD   -> ModPanel(adminViewModel = adminViewModel)
            else           -> {}
        }

        // --- AI Settings ---
        AiSettingsSection(
            currentApiKey = settings.geminiApiKey,
            currentPrompt = settings.translationPrompt,
            onSaveApiKey  = onSaveApiKey,
            onSavePrompt  = onSavePrompt
        )
    }
}

// ============================================================
// USER HEADER — Avatar, tên, role badge, edit
// ============================================================

@Composable
fun UserHeader(user: User?, onUpdateProfile: (String, String) -> Unit) {
    var showEditDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape)
                .background(roleBgColor(user?.userRole)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user?.name?.take(1)?.uppercase() ?: "?",
                fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user?.name ?: "Người dùng", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                RoleBadge(role = user?.userRole ?: UserRole.USER)
            }
            Text(user?.email ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (user?.banInfo?.isActive == true) {
                Spacer(Modifier.height(4.dp))
                Text("⚠️ Tài khoản bị khóa: ${user.banInfo.reason}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }

        IconButton(onClick = { showEditDialog = true }) {
            Icon(Icons.Default.Edit, "Chỉnh sửa", tint = MaterialTheme.colorScheme.primary)
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            currentName  = user?.name ?: "",
            onDismiss    = { showEditDialog = false },
            onSave       = { name -> onUpdateProfile(name, ""); showEditDialog = false }
        )
    }
}

@Composable
fun EditProfileDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa hồ sơ") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Tên hiển thị") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSave(name) }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

// ============================================================
// ROLE BADGE
// ============================================================

@Composable
fun RoleBadge(role: UserRole) {
    val (color, label) = when (role) {
        UserRole.ADMIN -> MaterialTheme.colorScheme.error to "Admin"
        UserRole.MOD   -> MaterialTheme.colorScheme.tertiary to "Mod"
        UserRole.USER  -> MaterialTheme.colorScheme.secondary to "User"
    }
    Surface(color = color, shape = RoundedCornerShape(4.dp)) {
        Text(label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun roleBgColor(role: UserRole?): Color {
    val scheme = MaterialTheme.colorScheme
    return when (role) {
        UserRole.ADMIN -> scheme.error
        UserRole.MOD   -> scheme.tertiary
        else           -> scheme.primary
    }
}

// ============================================================
// ADMIN PANEL — Quản lý người dùng + Mod
// ============================================================

@Composable
fun AdminPanel(adminViewModel: AdminViewModel) {
    val actionState by adminViewModel.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionState) {
        when (actionState) {
            is AdminActionState.Success ->
                snackbarHostState.showSnackbar((actionState as AdminActionState.Success).message)
            is AdminActionState.Error ->
                snackbarHostState.showSnackbar((actionState as AdminActionState.Error).message)
            else -> {}
        }
        if (actionState !is AdminActionState.Idle) adminViewModel.clearActionState()
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Bảng quản trị Admin", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error)
        }

        // Thống kê nhanh
        AdminStatsRow(adminViewModel)

        // Danh sách người dùng
        UserManagementList(adminViewModel = adminViewModel, isAdmin = true)

        SnackbarHost(snackbarHostState)
    }
}

// ============================================================
// MOD PANEL — Chỉ quản lý người dùng (USER), không đổi role
// ============================================================

@Composable
fun ModPanel(adminViewModel: AdminViewModel) {
    val actionState by adminViewModel.actionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionState) {
        when (actionState) {
            is AdminActionState.Success ->
                snackbarHostState.showSnackbar((actionState as AdminActionState.Success).message)
            is AdminActionState.Error ->
                snackbarHostState.showSnackbar((actionState as AdminActionState.Error).message)
            else -> {}
        }
        if (actionState !is AdminActionState.Idle) adminViewModel.clearActionState()
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(8.dp))
            Text("Công cụ Mod", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.tertiary)
        }
        UserManagementList(adminViewModel = adminViewModel, isAdmin = false)
        SnackbarHost(snackbarHostState)
    }
}

// ============================================================
// THỐNG KÊ NHANH (Admin)
// ============================================================

@Composable
fun AdminStatsRow(adminViewModel: AdminViewModel) {
    val users by adminViewModel.users.collectAsState()
    val totalUsers  = users.size
    val totalMods   = users.count { it.role == UserRole.MOD.label }
    val totalBanned = users.count { it.isBanned }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("👥 Tổng", totalUsers.toString(), modifier = Modifier.weight(1f))
        StatCard("🛡 Mod", totalMods.toString(), modifier = Modifier.weight(1f))
        StatCard("🔒 Bị khóa", totalBanned.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================
// DANH SÁCH NGƯỜI DÙNG + HÀNH ĐỘNG
// ============================================================

@Composable
fun UserManagementList(adminViewModel: AdminViewModel, isAdmin: Boolean) {
    val filteredUsers by adminViewModel.filteredUsers.collectAsState()
    val searchQuery by adminViewModel.searchQuery.collectAsState()
    val actionState by adminViewModel.actionState.collectAsState()
    var selectedUser by remember { mutableStateOf<UserSummary?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Thanh tìm kiếm
        OutlinedTextField(
            value = searchQuery,
            onValueChange = adminViewModel::onSearchQueryChange,
            placeholder = { Text("Tìm theo tên, email, role...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // Danh sách — dùng Column thay LazyColumn vì đã nằm trong verticalScroll
        filteredUsers.forEach { user ->
            UserListItem(
                user    = user,
                isAdmin = isAdmin,
                onAction = { selectedUser = user }
            )
        }

        if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Không tìm thấy người dùng", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Dialog hành động khi chọn user
    selectedUser?.let { user ->
        UserActionDialog(
            user      = user,
            isAdmin   = isAdmin,
            isLoading = actionState is AdminActionState.Loading,
            onBan     = { reason, hours -> adminViewModel.banUser(user.id, reason, hours); selectedUser = null },
            onUnban   = { adminViewModel.unbanUser(user.id); selectedUser = null },
            onPromote = { adminViewModel.promoteToMod(user.id); selectedUser = null },
            onDemote  = { adminViewModel.demoteToUser(user.id); selectedUser = null },
            onDismiss = { selectedUser = null }
        )
    }
}

@Composable
fun UserListItem(user: UserSummary, isAdmin: Boolean, onAction: () -> Unit) {
    val role = UserRole.fromString(user.role)
    // Mod chỉ thấy USER; Admin thấy tất cả trừ Admin khác
    if (!isAdmin && role != UserRole.USER) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.isBanned)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini avatar
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(roleBgColor(role)),
                contentAlignment = Alignment.Center
            ) {
                Text(user.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    RoleBadge(role = role)
                    if (user.isBanned) {
                        Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(4.dp)) {
                            Text("Khóa", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
                Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (user.isBanned && user.banReason.isNotBlank()) {
                    Text("Lý do: ${user.banReason}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            // Chỉ không hiện nút cho Admin khác
            if (role != UserRole.ADMIN) {
                IconButton(onClick = onAction) {
                    Icon(Icons.Default.MoreVert, "Tùy chọn")
                }
            }
        }
    }
}

// ============================================================
// USER ACTION DIALOG
// ============================================================

@Composable
fun UserActionDialog(
    user: UserSummary,
    isAdmin: Boolean,
    isLoading: Boolean,
    onBan: (String, Long) -> Unit,
    onUnban: () -> Unit,
    onPromote: () -> Unit,
    onDemote: () -> Unit,
    onDismiss: () -> Unit
) {
    var showBanDialog by remember { mutableStateOf(false) }

    if (showBanDialog) {
        BanDialog(
            userName  = user.name,
            onConfirm = { reason, hours -> onBan(reason, hours) },
            onDismiss = { showBanDialog = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(user.name, fontWeight = FontWeight.Bold)
                RoleBadge(UserRole.fromString(user.role))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(user.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Khóa / Mở khóa
                if (user.isBanned) {
                    OutlinedButton(onClick = onUnban, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mở khóa tài khoản")
                    }
                } else {
                    Button(
                        onClick = { showBanDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Khóa tài khoản")
                    }
                }

                // Admin-only: thay đổi role
                if (isAdmin) {
                    HorizontalDivider()
                    when (UserRole.fromString(user.role)) {
                        UserRole.USER -> {
                            OutlinedButton(onClick = onPromote, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Thăng lên Mod")
                            }
                        }
                        UserRole.MOD -> {
                            OutlinedButton(onClick = onDemote, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hạ về Người dùng")
                            }
                        }
                        else -> {}
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Hủy") }
            }
        },
        dismissButton = {}
    )
}

// ============================================================
// BAN DIALOG — nhập lý do + thời hạn
// ============================================================

@Composable
fun BanDialog(
    userName: String,
    onConfirm: (reason: String, durationHours: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var durationOption by remember { mutableStateOf(0) } // 0=vĩnh viễn, 1=24h, 2=72h, 3=168h

    val durationOptions = listOf(
        "Vĩnh viễn" to 0L,
        "24 giờ"    to 24L,
        "3 ngày"    to 72L,
        "7 ngày"    to 168L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Khóa tài khoản: $userName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Lý do *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Text("Thời hạn:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                durationOptions.forEachIndexed { index, (label, _) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = durationOption == index,
                            onClick  = { durationOption = index }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }

                if (reason.isBlank()) {
                    Text("Vui lòng nhập lý do khóa", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hours = durationOptions[durationOption].second
                    onConfirm(reason, hours)
                    onDismiss()
                },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Xác nhận khóa") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

// ============================================================
// AI SETTINGS
// ============================================================

@Composable
fun AiSettingsSection(
    currentApiKey: String,
    currentPrompt: String,
    onSaveApiKey: (String) -> Unit,
    onSavePrompt: (String) -> Unit
) {
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var prompt by remember(currentPrompt) { mutableStateOf(currentPrompt) }
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeySaved by remember { mutableStateOf(false) }
    var promptSaved by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("⚙️ Cài đặt AI Dịch", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        Card(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Gemini API Key", fontWeight = FontWeight.Medium)
                }
                Text("Lấy API key miễn phí tại aistudio.google.com",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it; apiKeySaved = false },
                    placeholder = { Text("AIzaSy...") },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (apiKeySaved) {
                        Text("✓ Đã lưu", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp))
                    }
                    FilledTonalButton(onClick = { onSaveApiKey(apiKey); apiKeySaved = true }) {
                        Text("Lưu API Key")
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Prompt dịch truyện", fontWeight = FontWeight.Medium)
                }
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it; promptSaved = false },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5, maxLines = 12,
                    placeholder = { Text(DEFAULT_TRANSLATION_PROMPT, fontSize = 12.sp) }
                )
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { prompt = DEFAULT_TRANSLATION_PROMPT; promptSaved = false }) {
                        Text("Khôi phục mặc định")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (promptSaved) {
                            Text("✓ Đã lưu", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                                modifier = Modifier.padding(end = 8.dp))
                        }
                        FilledTonalButton(onClick = { onSavePrompt(prompt); promptSaved = true }) {
                            Text("Lưu Prompt")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// READER / APP SETTINGS (luôn hiển thị)
// ============================================================

@Composable
fun ReaderSettingsSection() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("📚 Ứng dụng", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        val menuItems = listOf(
            Icons.Default.Storage    to "Lưu trữ",
            Icons.Default.BarChart   to "Thống kê",
            Icons.Default.Extension  to "Phần mở rộng",
            Icons.Default.Sync       to "Đồng bộ & sao lưu",
            Icons.Default.Settings   to "Cài đặt"
        )

        Card(shape = RoundedCornerShape(12.dp)) {
            Column {
                menuItems.forEachIndexed { index, (icon, label) ->
                    ListItem(
                        headlineContent  = { Text(label) },
                        leadingContent   = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent  = { Icon(Icons.Default.ChevronRight, null) }
                    )
                    if (index < menuItems.size - 1) HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("🔗 Kết nối", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        Card(shape = RoundedCornerShape(12.dp)) {
            Column {
                ListItem(
                    headlineContent = { Text("Chế độ nhà phát triển") },
                    leadingContent  = { Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Switch(checked = false, onCheckedChange = {}) }
                )
                HorizontalDivider(thickness = 0.5.dp)
                ListItem(
                    headlineContent = { Text("Mời bạn bè sử dụng") },
                    leadingContent  = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
                HorizontalDivider(thickness = 0.5.dp)
                ListItem(
                    headlineContent = { Text("Theo dõi Fanpage") },
                    leadingContent  = { Icon(Icons.Default.Facebook, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Phiên bản: 1.0.0",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
