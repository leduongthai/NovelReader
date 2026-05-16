package com.example.novelreader.presentation.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun AvatarImage(
    avatarUrl: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary
) {
    val safeAvatar = avatarUrl.orEmpty()
    val bitmap = remember(safeAvatar) { decodeDataUrlImage(safeAvatar) }
    val initial = displayName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "?"

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            safeAvatar.isNotBlank() -> AsyncImage(
                model = safeAvatar,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            else -> Text(
                text = initial,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun decodeDataUrlImage(dataUrl: String) = runCatching {
    if (!dataUrl.startsWith("data:image", ignoreCase = true)) return@runCatching null
    val base64 = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
    if (base64.isBlank()) return@runCatching null
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
