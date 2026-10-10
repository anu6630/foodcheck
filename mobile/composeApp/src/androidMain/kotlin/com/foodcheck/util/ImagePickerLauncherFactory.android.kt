package com.foodcheck.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

private const val TAG = "FoodCheck.ImagePicker"
private const val MAX_DIMENSION = 1280
private const val JPEG_QUALITY = 80

@Composable
fun rememberImagePickerLauncher(): ImagePickerLauncher {
    val context = LocalContext.current
    // Must survive recomposition — a plain local var is reset each frame and the
    // activity-result callback then resumes a null continuation (silent no-op).
    val pending = remember { AtomicReference<CancellableContinuation<ByteArray?>?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cont = pending.getAndSet(null)
        if (cont == null) {
            Log.e(TAG, "Gallery result with no pending continuation (recomposition bug)")
            return@rememberLauncherForActivityResult
        }
        if (!cont.isActive) {
            Log.w(TAG, "Gallery result but continuation already cancelled")
            return@rememberLauncherForActivityResult
        }
        val bytes = try {
            uri?.let { compressUriToJpeg(context, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Gallery compress failed", e)
            null
        }
        Log.i(TAG, "Gallery picked bytes=${bytes?.size ?: 0}")
        cont.resume(bytes) { _, _, _ -> }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val cont = pending.getAndSet(null)
        if (cont == null || !cont.isActive) {
            Log.e(TAG, "Camera result with no active continuation")
            return@rememberLauncherForActivityResult
        }
        val bytes = try {
            bitmap?.let { compressBitmapToJpeg(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Camera compress failed", e)
            null
        }
        Log.i(TAG, "Camera picked bytes=${bytes?.size ?: 0}")
        cont.resume(bytes) { _, _, _ -> }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                Log.e(TAG, "Camera launch after permission failed", e)
                pending.getAndSet(null)?.takeIf { it.isActive }?.resume(null) { _, _, _ -> }
            }
        } else {
            Log.w(TAG, "CAMERA permission denied")
            pending.getAndSet(null)?.takeIf { it.isActive }?.resume(null) { _, _, _ -> }
        }
    }

    return remember(galleryLauncher, cameraLauncher, cameraPermissionLauncher) {
        ImagePickerLauncher { source ->
            suspendCancellableCoroutine { cont ->
                pending.set(cont)
                cont.invokeOnCancellation { pending.compareAndSet(cont, null) }
                when (source) {
                    ImagePickerSource.GALLERY -> galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    ImagePickerSource.CAMERA -> {
                        if (hasCameraPermission(context)) {
                            try {
                                cameraLauncher.launch(null)
                            } catch (e: Exception) {
                                Log.e(TAG, "Camera launch failed", e)
                                pending.getAndSet(null)
                                cont.resume(null) { _, _, _ -> }
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun compressUriToJpeg(context: Context, uri: Uri): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        Log.e(TAG, "decode bounds failed for $uri")
        return null
    }

    var sample = 1
    while (bounds.outWidth / sample > MAX_DIMENSION * 2 || bounds.outHeight / sample > MAX_DIMENSION * 2) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null
    return compressBitmapToJpeg(decoded, recycleSource = true)
}

private fun compressBitmapToJpeg(bitmap: Bitmap, recycleSource: Boolean = false): ByteArray {
    val scaled = scaleToMax(bitmap, MAX_DIMENSION)
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
    if (scaled !== bitmap) {
        scaled.recycle()
        if (recycleSource) bitmap.recycle()
    } else if (recycleSource) {
        bitmap.recycle()
    }
    return stream.toByteArray()
}

private fun scaleToMax(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val longest = max(w, h)
    if (longest <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longest
    val nw = (w * scale).toInt().coerceAtLeast(1)
    val nh = (h * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
}
