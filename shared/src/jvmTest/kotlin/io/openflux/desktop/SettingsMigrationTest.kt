package io.openflux.desktop

import io.openflux.desktop.data.FileSettingsRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsMigrationTest {
    @Test
    fun systemProxyTurnsOnOnceForOldSettings() {
        val dir = Files.createTempDirectory("openflux-settings").toFile()
        try {
            // Saved by a version where the system proxy was off by default.
            dir.resolve("settings.json").writeText("""{"systemProxy": false, "socksPort": 1090}""")
            val first = FileSettingsRepository(dir)
            assertTrue(first.settings.value.systemProxy)
            assertTrue(first.settings.value.socksPort == 1090)

            // Turned off by hand afterwards: that choice stays.
            first.update { it.copy(systemProxy = false) }
            assertFalse(FileSettingsRepository(dir).settings.value.systemProxy)

            // A fresh install starts with it on.
            val fresh = Files.createTempDirectory("openflux-settings").toFile()
            assertTrue(FileSettingsRepository(fresh).settings.value.systemProxy)
            fresh.deleteRecursively()
        } finally {
            dir.deleteRecursively()
        }
    }
}
