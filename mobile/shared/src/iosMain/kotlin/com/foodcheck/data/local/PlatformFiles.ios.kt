package com.foodcheck.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
internal actual object PlatformFiles {
    actual fun ensureDir(path: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    actual fun readUtf8(path: String): String? {
        return NSString.stringWithContentsOfFile(
            path = path,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }

    actual fun writeUtf8(path: String, content: String) {
        (content as NSString).writeToFile(
            path = path,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }
}
