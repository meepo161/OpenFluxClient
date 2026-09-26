package io.openflux.desktop.data

import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.Profile
import io.openflux.desktop.service.ProfileRepository
import io.openflux.desktop.service.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

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

internal val StoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/** A JSON file written atomically: a temp file renamed over the old one. */
internal class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>) {
    fun read(): T? = runCatching {
        if (!file.isFile) null else StoreJson.decodeFromString(serializer, file.readText())
    }.getOrNull()

    fun write(value: T) {
        file.parentFile.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(StoreJson.encodeToString(serializer, value))
        restrictToOwner(tmp)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

/** Makes a file readable by its owner only where the file system allows it. */
internal fun restrictToOwner(file: File) {
    file.setReadable(false, false)
    file.setReadable(true, true)
    file.setWritable(false, false)
    file.setWritable(true, true)
}

class FileProfileRepository(dir: File = AppDirs.config) : ProfileRepository {
    private val store = JsonFile(File(dir, "profiles.json"), ListSerializer(Profile.serializer()))
    private val state = MutableStateFlow(store.read().orEmpty())
    override val profiles: StateFlow<List<Profile>> = state.asStateFlow()

    @Synchronized
    override fun upsert(profile: Profile) {
        val current = state.value
        val next = if (current.any { it.id == profile.id }) {
            current.map { if (it.id == profile.id) profile else it }
        } else {
            current + profile
        }
        store.write(next)
        state.value = next
    }

    @Synchronized
    override fun delete(id: String) {
        val next = state.value.filterNot { it.id == id }
        store.write(next)
        state.value = next
    }

    override fun newId(): String = UUID.randomUUID().toString()
}

class FileSettingsRepository(dir: File = AppDirs.config) : SettingsRepository {
    private val store = JsonFile(File(dir, "settings.json"), AppSettings.serializer())
    private val state = MutableStateFlow(store.read() ?: AppSettings())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    @Synchronized
    override fun update(transform: (AppSettings) -> AppSettings) {
        state.update { old ->
            transform(old).also { if (it != old) store.write(it) }
        }
    }
}
