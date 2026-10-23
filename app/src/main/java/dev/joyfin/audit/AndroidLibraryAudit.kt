package dev.joyfin.audit

import android.content.Context
import dev.joyfin.data.*
import dev.joyfin.data.subtitles.LocalSubtitleStore
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import java.io.File

internal class FileAuditPersistence(private val directory: File) : AuditPersistence {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "library-audit"))
    private fun file(owner: Session, scope: AuditScope): File {
        val account = auditDigest(owner.server.trimEnd('/') + "\u0000" + owner.userId)
        val range = auditDigest(scope.id?.let { "library:$it" } ?: "all")
        return File(directory, "$account-$range.json")
    }
    override suspend fun load(owner: Session, scope: AuditScope): AuditSaved? = withContext(Dispatchers.IO) {
        val file = file(owner, scope)
        if (!file.isFile) null else {
            check(file.length() <= 32L * 1024 * 1024) { "Audit cache too large" }
            AppJson.decodeFromString<AuditSaved>(file.readText())
        }
    }
    override suspend fun save(owner: Session, saved: AuditSaved) = withContext(Dispatchers.IO) {
        val encoded = AppJson.encodeToString(saved).toByteArray(Charsets.UTF_8)
        check(encoded.size <= 32 * 1024 * 1024) { "Audit cache too large" }
        check(directory.isDirectory || directory.mkdirs())
        val file = file(owner, saved.report.scope)
        val temporary = File(directory, file.name + ".tmp")
        temporary.writeBytes(encoded)
        check(temporary.renameTo(file)) { "Cannot save audit" }
    }
}

fun createLibraryAuditController(context: Context, scope: CoroutineScope, api: JellyfinApi,
    expired: () -> Unit): LibraryAuditController {
    val subtitles = LocalSubtitleStore.get(context)
    val source = object : LibraryAuditSource {
        override suspend fun works(owner: Session, scope: AuditScope, start: Int) = api.auditWorks(owner, scope.id, start)
        override suspend fun episodes(owner: Session, series: String, missing: Boolean, start: Int) = api.auditEpisodes(owner, series, missing, start)
        override suspend fun item(owner: Session, id: String) = api.auditItem(owner, id)
    }
    val repository = LibraryAuditRepository(source, { owner ->
        val bindings = withContext(Dispatchers.IO) { subtitles.availableBindingSnapshot() }
        val available: (String, String) -> Boolean? = { item, version ->
            LocalSubtitleStore.key(owner.server, owner.userId, item, version) in bindings
        }
        available
    })
    return LibraryAuditController(scope, repository, FileAuditPersistence(context), { api.session }, expired)
}
