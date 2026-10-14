package dev.joyfin.data.subtitles

import java.io.ByteArrayOutputStream
import java.lang.ref.Reference
import java.nio.ByteBuffer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException

/** Reads RAR4/5 into memory; archive paths are never used as filesystem destinations. */
internal object RarSubtitleArchive {
    suspend fun unpack(bytes: ByteArray, limit: Int, supported: (String) -> Boolean): List<SubtitleFiles.Entry> {
        try {
            // libarchive retains this address until free(). The 1.1.6 heap-buffer JNI path
            // retains a local array reference, then releases it as a global reference.
            // Use direct memory and fence its lifetime after native cleanup instead.
            val input = ByteBuffer.allocateDirect(bytes.size)
            input.put(bytes)
            input.flip()
            val archive = Archive.readNew()
            try {
                Archive.readSupportFilterNone(archive)
                Archive.readSupportFormatRar(archive)
                Archive.readSupportFormatRar5(archive)
                Archive.readOpenMemory(archive, input)
                val files = mutableListOf<SubtitleFiles.Entry>()
                val buffer = ByteBuffer.allocateDirect(8192)
                val chunk = ByteArray(8192)
                var count = 0
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = Archive.readNextHeader(archive)
                    if (entry == 0L) break
                    checkFile(++count <= 200, "压缩包超过 200 个条目限制")
                    checkFile(!ArchiveEntry.isEncrypted(entry), "RAR 含加密文件，暂不支持密码解压")
                    if (ArchiveEntry.filetype(entry) != ArchiveEntry.AE_IFREG || ArchiveEntry.hardlink(entry) != null) {
                        Archive.readDataSkip(archive)
                        continue
                    }
                    val name = ArchiveEntry.pathnameUtf8(entry)
                        ?: throw SubtitleOperationException("RAR 文件名无法读取")
                    val keep = supported(name)
                    val output = if (keep) ByteArrayOutputStream() else null
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        buffer.clear()
                        Archive.readData(archive, buffer)
                        val size = buffer.position()
                        if (size == 0) break
                        checkFile(size <= limit - total, "字幕下载或解压内容超过 20 MiB 限制")
                        total += size
                        if (output != null) {
                            buffer.flip()
                            buffer.get(chunk, 0, size)
                            output.write(chunk, 0, size)
                        }
                    }
                    if (output != null) files += SubtitleFiles.Entry(name, output.toByteArray())
                }
                checkFile(files.isNotEmpty(), "压缩包内没有 SRT、VTT、ASS 或 SSA 字幕")
                return files
            } finally {
                try {
                    Archive.free(archive)
                } finally {
                    Reference.reachabilityFence(input)
                }
            }
        } catch (_: ArchiveException) {
            throw SubtitleOperationException("RAR 解压失败，请检查文件是否完整；暂不支持加密或分卷压缩包")
        } catch (_: UnsatisfiedLinkError) {
            throw SubtitleOperationException("RAR 解压组件加载失败")
        }
    }

    private fun checkFile(value: Boolean, message: String) {
        if (!value) throw SubtitleOperationException(message)
    }
}
