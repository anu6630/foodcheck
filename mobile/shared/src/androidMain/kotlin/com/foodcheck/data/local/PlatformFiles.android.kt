package com.foodcheck.data.local

import java.io.File

internal actual object PlatformFiles {
    actual fun ensureDir(path: String) {
        File(path).mkdirs()
    }

    actual fun readUtf8(path: String): String? {
        val file = File(path)
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    actual fun writeUtf8(path: String, content: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }
}
