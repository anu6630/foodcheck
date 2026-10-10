package com.foodcheck.util

import androidx.compose.ui.graphics.ImageBitmap

/** Decode JPEG/PNG (or platform-supported) bytes into a Compose [ImageBitmap], or null on failure. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
