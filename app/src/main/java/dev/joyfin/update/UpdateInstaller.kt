package dev.joyfin.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

internal class UpdateInstaller(private val context: Context) {
    val directory: File get() = File(context.cacheDir, "updates")

    @Suppress("DEPRECATION")
    fun verify(file: File, release: AppRelease) {
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archive = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("无法识别下载的安装包")
        check(archive.packageName == context.packageName) { "安装包不属于 Joyfin" }
        check(archive.versionName == release.version && archive.longVersionCode > installed.longVersionCode) {
            "安装包版本不适合升级当前应用"
        }
        val minimum = archive.applicationInfo?.minSdkVersion ?: error("无法读取安装包系统要求")
        check(minimum <= Build.VERSION.SDK_INT) { "新版不支持当前 Android 系统" }
        val expected = signers(installed)
        check(expected.isNotEmpty() && signers(archive) == expected) {
            "新版与当前应用签名不一致，无法覆盖升级。开发版请通过正式发布页获取安装包。"
        }
    }

    private fun signers(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners.orEmpty()
        .map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()

    fun allowed(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"))

    fun installIntent(file: File): Intent {
        check(file.isFile && file.parentFile?.canonicalFile == directory.canonicalFile) { "安装文件已失效，请重新下载" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("Joyfin update", uri) }
    }
}
