package com.optimizer.tools.data.profiles

import com.optimizer.tools.domain.profile.ProfilesDocument
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persistencia de los perfiles de apps.
 *
 * Guarda un unico documento con escritura atomica: un fallo a mitad de guardado deja el
 * archivo anterior intacto en lugar de dejar perfiles a medias.
 */
class ProfileStore(
    private val file: File,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            prettyPrint = true
        }

    private val lock = Any()

    private var document: ProfilesDocument = load()

    fun document(): ProfilesDocument = synchronized(lock) { document }

    fun update(transform: (ProfilesDocument) -> ProfilesDocument): ProfilesDocument =
        synchronized(lock) {
            val next = transform(document)
            if (next != document) {
                document = next
                persist(next)
            }
            document
        }

    private fun load(): ProfilesDocument {
        if (!file.exists()) return ProfilesDocument()
        return runCatching { json.decodeFromString<ProfilesDocument>(file.readText()) }
            .getOrElse { ProfilesDocument() }
    }

    private fun persist(next: ProfilesDocument) {
        val parent = file.absoluteFile.parentFile ?: return
        check(parent.exists() || parent.mkdirs()) { "No se pudo crear el directorio de perfiles." }
        val tmp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            tmp.writeText(json.encodeToString(ProfilesDocument.serializer(), next))
            try {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
