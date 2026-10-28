package com.foodcheck.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodcheck.presentation.theme.AppColors
import com.foodcheck.util.decodeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

private val avatarMemoryCache = mutableMapOf<String, ImageBitmap>()
private val avatarHttpClient by lazy { HttpClient() }

@Composable
fun UserAvatar(
    photoUrl: String?,
    displayName: String? = null,
    size: Dp = 40.dp,
    borderColor: Color = AppColors.Primary.copy(alpha = 0.6f),
    borderWidth: Dp = 1.5.dp,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(photoUrl) {
        mutableStateOf(photoUrl?.let { avatarMemoryCache[it] })
    }

    LaunchedEffect(photoUrl) {
        val url = photoUrl?.trim()
        if (url.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val response = avatarHttpClient.get(url)
                if (response.status.isSuccess()) {
                    val bytes = response.readRawBytes()
                    val decoded = decodeImageBitmap(bytes)
                    if (decoded != null) {
                        avatarMemoryCache[url] = decoded
                        bitmap = decoded
                    }
                }
            } catch (_: Throwable) {
                // Ignore network errors, fall back to initials or icon
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AppColors.Primary.copy(alpha = 0.2f))
            .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = displayName ?: "User Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initials = remember(displayName) {
                displayName?.trim()?.split("\\s+".toRegex())
                    ?.take(2)
                    ?.mapNotNull { it.firstOrNull()?.uppercase() }
                    ?.joinToString("")
                    .orEmpty()
            }
            if (initials.isNotBlank()) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(size * 0.7f)
                )
            }
        }
    }
}
