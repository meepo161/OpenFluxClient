package io.openflux.desktop

import io.openflux.desktop.core.CoreBinary
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.CoreSource
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreBinaryTest {
    /** A package can drop the core's executable bit (Linux and macOS need it). */
    @Test
    fun aCoreWithoutTheExecutableBitStillRuns() {
        if (System.getProperty("os.name").lowercase().contains("win")) return
        val dir = Files.createTempDirectory("openflux-core").toFile()
        try {
            val core = dir.resolve("openflux-linux-amd64").apply { writeText("#!/bin/sh\n"); setExecutable(false, false) }
            val resolved = CoreBinary().resolve(AppSettings(coreSource = CoreSource.Custom, customCorePath = core.absolutePath))
            assertNotNull(resolved)
            assertTrue(resolved.canExecute())
            assertNull(CoreBinary().resolve(AppSettings(coreSource = CoreSource.Custom, customCorePath = dir.resolve("missing").absolutePath)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
