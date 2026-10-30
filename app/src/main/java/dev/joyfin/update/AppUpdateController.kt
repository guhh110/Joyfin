package dev.joyfin.update

import android.content.Context
import android.content.Intent
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, CURRENT, DOWNLOADING, READY }

data class AppUpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: AppRelease? = null,
    val progress: Int = 0,
    val error: String? = null,
) {
    val busy: Boolean get() = phase == UpdatePhase.CHECKING || phase == UpdatePhase.DOWNLOADING
}

class AppUpdateController(context: Context, private val scope: CoroutineScope) {
    private val repository = ReleaseRepository()
    private val installer = UpdateInstaller(context.applicationContext)
    private val mutable = MutableStateFlow(AppUpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var readyFile: File? = null
    private var generation = 0L

    fun check() {
        if (mutable.value.busy) return
        val token = ++generation
        mutable.value = AppUpdateState(phase = UpdatePhase.CHECKING)
        job = scope.launch {
            try {
                val release = repository.latest()
                ensureActive()
                if (token == generation) mutable.value = AppUpdateState(
                    phase = if (release == null) UpdatePhase.CURRENT else UpdatePhase.AVAILABLE, release = release)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (token == generation) mutable.value = AppUpdateState(error = message(e, "无法检查更新，请检查网络后重试"))
            }
        }
    }

    fun download() {
        val release = mutable.value.release ?: return
        if (mutable.value.busy) return
        val token = ++generation
        mutable.value = mutable.value.copy(phase = UpdatePhase.DOWNLOADING, progress = 0, error = null)
        job = scope.launch {
            var downloaded: File? = null
            try {
                val directory = installer.directory
                check(directory.isDirectory || directory.mkdirs()) { "无法创建更新目录" }
                withContext(Dispatchers.IO) {
                    val staleBefore = System.currentTimeMillis() - 24 * 60 * 60_000L
                    directory.listFiles()?.filter {
                        it.extension in setOf("apk", "part") && it.lastModified() < staleBefore
                    }?.forEach { it.delete() }
                }
                val file = File.createTempFile("download-", ".apk", directory)
                downloaded = file
                readyFile = null
                repository.download(release, file) { progress ->
                    withContext(Dispatchers.Main.immediate) {
                        if (token == generation) mutable.update { it.copy(progress = progress) }
                    }
                }
                withContext(Dispatchers.IO) { installer.verify(file, release) }
                ensureActive()
                if (token != generation) return@launch
                val destination = File(installer.directory, release.apkName)
                check(file.renameTo(destination)) { "无法保存安装包，请重新下载" }
                readyFile = destination
                downloaded = null
                mutable.value = mutable.value.copy(phase = UpdatePhase.READY, progress = 100)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (token == generation) mutable.value = mutable.value.copy(phase = UpdatePhase.AVAILABLE,
                    error = message(e, "下载失败，请检查网络和存储空间后重试"))
            } finally { downloaded?.delete() }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        if (mutable.value.busy) mutable.value = mutable.value.copy(
            phase = if (mutable.value.release != null) UpdatePhase.AVAILABLE else UpdatePhase.IDLE,
            progress = 0, error = null)
    }

    fun installAllowed(): Boolean = installer.allowed()
    fun permissionIntent(): Intent = installer.permissionIntent()
    fun installIntent(): Intent {
        val file = readyFile
        if (file == null || !file.isFile) {
            mutable.update { it.copy(phase = UpdatePhase.AVAILABLE) }
            error("安装文件已失效，请重新下载")
        }
        return installer.installIntent(file)
    }
    fun installError(message: String) { mutable.update { it.copy(error = message) } }
    fun clearError() { mutable.update { it.copy(error = null) } }

    private fun message(error: Exception, fallback: String): String =
        if (error is IllegalStateException) error.message ?: fallback else fallback
}
