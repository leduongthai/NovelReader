package com.example.novelreader.presentation.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.novelreader.domain.model.DEFAULT_TRANSLATION_PROMPT
import com.example.novelreader.presentation.viewmodel.AuthState
import com.example.novelreader.presentation.viewmodel.ProfileViewModel

// ============================================================
// PROFILE SCREEN — Cá nhân
// Matches screenshot: avatar, name, settings list
// ============================================================

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cá nhân") },
                actions = {
                    if (viewModel.isLoggedIn) {
                        TextButton(onClick = { viewModel.signOut() }) {
                            Text("Đăng xuất")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            if (viewModel.isLoggedIn) {
                LoggedInProfile(
                    authState = authState,
                    settings = settings,
                    onSaveApiKey = viewModel::saveApiKey,
                    onSavePrompt = viewModel::saveTranslationPrompt
                )
            } else {
                AuthSection(
                    authState = authState,
                    onSignIn = viewModel::signIn,
                    onSignUp = viewModel::signUp,
                    onResetPassword = viewModel::resetPassword
                )
            }

            // Reader settings always visible regardless of login
            Spacer(Modifier.height(16.dp))
            ReaderSettingsSection(settings = settings)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isSignUp) "Đăng ký tài khoản" else "Đăng nhập",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            if (isSignUp) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hiển thị") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mật khẩu") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            // Error message
            if (authState is AuthState.Error) {
                Text(
                    (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            // Submit button
            Button(
                onClick = {
                    if (isSignUp) onSignUp(name, email, password)
                    else onSignIn(email, password)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(if (isSignUp) "Đăng ký" else "Đăng nhập")
                }
            }

            if (!isSignUp) {
                // Hiển thị thông báo khi gửi email thành công
                if (authState is AuthState.PasswordResetSent) {
                    Text(
                        "✓ Đã gửi email đặt lại mật khẩu. Kiểm tra hộp thư của bạn.",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                TextButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Quên mật khẩu?", fontSize = 13.sp)
                }


                // Toggle sign-up / sign-in
                TextButton(
                    onClick = { isSignUp = !isSignUp; name = ""; email = ""; password = "" },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        if (isSignUp) "Đã có tài khoản? Đăng nhập"
                        else "Chưa có tài khoản? Đăng ký"
                    )
                }
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
                    Text("Nhập email của bạn để nhận liên kết đặt lại mật khẩu.", fontSize = 14.sp)
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetPassword(resetEmail)
                    showResetDialog = false
                }) { Text("Gửi") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Hủy") }
            }
        )
    }
}

// ============================================================
// LOGGED-IN PROFILE VIEW
// ============================================================

@Composable
fun LoggedInProfile(
    authState: AuthState,
    settings: com.example.novelreader.domain.model.ReaderSettings,
    onSaveApiKey: (String) -> Unit,
    onSavePrompt: (String) -> Unit
) {
    val user = (authState as? AuthState.LoggedIn)?.user

    Column(modifier = Modifier.fillMaxWidth()) {
        // Avatar + user info header
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user?.name?.take(1)?.uppercase() ?: "?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user?.name ?: "Người dùng",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (user?.isPremium == true) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Premium",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                }
                Text(
                    user?.email ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        // AI Settings section
        AiSettingsSection(
            currentApiKey = settings.geminiApiKey,
            currentPrompt = settings.translationPrompt,
            onSaveApiKey = onSaveApiKey,
            onSavePrompt = onSavePrompt
        )
    }
}

// ============================================================
// AI SETTINGS (Gemini API Key + Translation Prompt)
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
        Text(
            "⚙️ Cài đặt AI Dịch",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        // Gemini API Key
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Gemini API Key", fontWeight = FontWeight.Medium)
                }
                Text(
                    "Lấy API key miễn phí tại aistudio.google.com",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; apiKeySaved = false },
                    placeholder = { Text("AIzaSy...") },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null
                            )
                        }
                    },
                    visualTransformation = if (showApiKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (apiKeySaved) {
                        Text("✓ Đã lưu", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp))
                    }
                    FilledTonalButton(onClick = {
                        onSaveApiKey(apiKey)
                        apiKeySaved = true
                    }) {
                        Text("Lưu API Key")
                    }
                }
            }
        }

        // Translation Prompt
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Prompt dịch truyện", fontWeight = FontWeight.Medium)
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it; promptSaved = false },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 12,
                    placeholder = { Text(DEFAULT_TRANSLATION_PROMPT, fontSize = 12.sp) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        prompt = DEFAULT_TRANSLATION_PROMPT
                        promptSaved = false
                    }) {
                        Text("Khôi phục mặc định")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (promptSaved) {
                            Text("✓ Đã lưu", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                                modifier = Modifier.padding(end = 8.dp))
                        }
                        FilledTonalButton(onClick = {
                            onSavePrompt(prompt)
                            promptSaved = true
                        }) {
                            Text("Lưu Prompt")
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// READER SETTINGS (always visible)
// ============================================================

@Composable
fun ReaderSettingsSection(settings: com.example.novelreader.domain.model.ReaderSettings) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("📚 Ứng dụng", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        val menuItems = listOf(
            Icons.Default.Storage to "Lưu trữ",
            Icons.Default.BarChart to "Thống kê",
            Icons.Default.Extension to "Phần mở rộng",
            Icons.Default.Sync to "Đồng bộ & sao lưu",
            Icons.Default.Settings to "Cài đặt"
        )

        Card(shape = RoundedCornerShape(12.dp)) {
            Column {
                menuItems.forEachIndexed { index, (icon, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) }
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
                    leadingContent = { Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Switch(checked = false, onCheckedChange = {}) }
                )
                HorizontalDivider(thickness = 0.5.dp)
                ListItem(
                    headlineContent = { Text("Mời bạn bè sử dụng") },
                    leadingContent = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
                HorizontalDivider(thickness = 0.5.dp)
                ListItem(
                    headlineContent = { Text("Theo dõi Fanpage") },
                    leadingContent = { Icon(Icons.Default.Facebook, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Version info
        Text(
            "Phiên bản: 1.0.0",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
