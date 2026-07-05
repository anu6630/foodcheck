package com.foodcheck.app

import androidx.compose.ui.window.ComposeUIViewController
import com.foodcheck.util.ImagePickerLauncher
import com.foodcheck.util.ImagePickerSource
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIViewController

/**
 * Entry point called from Swift (`ContentView.swift`). All native picker UI (PHPickerViewController /
 * UIImagePickerController) lives in Swift, not here - `pickImage` is a Swift closure that presents the
 * right picker for [ImagePickerSource] and invokes its callback with the picked image bytes (or null on
 * cancel). This keeps UIKit interop out of Kotlin/Native, where it would otherwise need hand-written
 * cinterop delegate protocol conformance.
 */
fun MainViewController(
    pickImage: (source: ImagePickerSource, onResult: (ByteArray?) -> Unit) -> Unit
): UIViewController {
    val launcher = ImagePickerLauncher { source ->
        suspendCancellableCoroutine { cont ->
            pickImage(source) { bytes -> cont.resume(bytes) { _, _, _ -> } }
        }
    }
    return ComposeUIViewController { App(imagePickerLauncher = launcher) }
}
