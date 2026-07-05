package com.foodcheck.util

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream

@Composable
fun rememberImagePickerLauncher(): ImagePickerLauncher {
    val context = LocalContext.current
    var pendingContinuation: CancellableContinuation<ByteArray?>? = null

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val bytes = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
        pendingContinuation?.resume(bytes) { _, _, _ -> }
        pendingContinuation = null
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val bytes = bitmap?.let {
            val stream = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
        pendingContinuation?.resume(bytes) { _, _, _ -> }
        pendingContinuation = null
    }

    return remember {
        ImagePickerLauncher { source ->
            suspendCancellableCoroutine { cont ->
                pendingContinuation = cont
                when (source) {
                    ImagePickerSource.GALLERY -> galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    ImagePickerSource.CAMERA -> cameraLauncher.launch(null)
                }
            }
        }
    }
}
