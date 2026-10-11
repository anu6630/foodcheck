package com.foodcheck.data.local

/**
 * Tiny cross-platform file helpers for durable app cache.
 * Call [AppFilesBootstrap.init] once at process start with a writable directory.
 */
object AppFiles {
    @Volatile
    private var rootDir: String? = null

    fun init(rootDirectory: String) {
        rootDir = rootDirectory
        PlatformFiles.ensureDir(rootDirectory)
    }

    fun readUtf8(name: String): String? {
        val root = rootDir ?: return null
        return PlatformFiles.readUtf8("$root/$name")
    }

    fun writeUtf8(name: String, content: String) {
        val root = rootDir ?: return
        PlatformFiles.ensureDir(root)
        PlatformFiles.writeUtf8("$root/$name", content)
    }
}

internal expect object PlatformFiles {
    fun ensureDir(path: String)
    fun readUtf8(path: String): String?
    fun writeUtf8(path: String, content: String)
}

/** Convenience for callers that already know the platform path. */
object AppFilesBootstrap {
    fun init(rootDirectory: String) = AppFiles.init(rootDirectory)
}
