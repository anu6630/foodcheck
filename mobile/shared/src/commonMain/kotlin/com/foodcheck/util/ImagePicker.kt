package com.foodcheck.util

enum class ImagePickerSource { CAMERA, GALLERY }

/**
 * Platform contract for picking/capturing a single image, returning its raw bytes (or null if the
 * user cancelled). Triggering a picker needs an Activity (Android) or a presenting UIViewController
 * (iOS), neither of which is available in commonMain, so unlike the rest of this module's
 * expect/actual platform code, the real implementations live in composeApp's androidMain/iosMain
 * and are handed down to shared UI as a plain callback — this interface just documents the shape
 * both platforms implement against.
 */
fun interface ImagePickerLauncher {
    suspend fun pickImage(source: ImagePickerSource): ByteArray?
}
