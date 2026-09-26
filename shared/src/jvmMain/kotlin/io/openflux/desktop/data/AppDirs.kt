package io.openflux.desktop.data

import java.io.File

/** Per-user application folders. */
object AppDirs {
    private val os = System.getProperty("os.name").lowercase()

    val config: File by lazy {
        val base = when {
            os.contains("win") -> System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), "AppData/Roaming")
            os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support")
            else -> System.getenv("XDG_CONFIG_HOME")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        }
        File(base, if (os.contains("win") || os.contains("mac")) "OpenFlux" else "openflux").apply { mkdirs() }
    }

    /** Files the core needs while it runs (key, .conf, IPC socket). */
    val runtime: File by lazy { File(config, "runtime").apply { mkdirs() } }
}
