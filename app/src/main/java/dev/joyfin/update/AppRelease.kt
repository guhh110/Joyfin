package dev.joyfin.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    companion object {
        fun parse(value: String): ReleaseVersion? {
            val match = Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matchEntire(value) ?: return null
            val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return ReleaseVersion(parts[0], parts[1], parts[2])
        }
    }
}

@Serializable
internal data class ReleaseAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val url: String,
    val state: String = "",
)

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

data class AppRelease(
    val version: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkBytes: Long,
    val checksumUrl: String,
) {
    val sizeLabel: String get() = "${apkBytes / 1024 / 1024} MB"
}

internal fun GitHubRelease.asUpdate(current: String): AppRelease? {
    val version = ReleaseVersion.parse(tag) ?: error("发布版本格式无效")
    val installed = ReleaseVersion.parse(current) ?: error("无法识别当前版本")
    check(!draft && !prerelease && tag.startsWith("v")) { "未找到可用的正式版本" }
    if (version <= installed) return null
    val name = "Joyfin-${tag.removePrefix("v")}.apk"
    val apk = assets.singleOrNull { it.name == name && it.state == "uploaded" }
        ?: error("新版安装包尚未就绪，请稍后重试")
    val checksum = assets.singleOrNull { it.name == "SHA256SUMS.txt" && it.state == "uploaded" }
        ?: error("新版校验文件尚未就绪，请稍后重试")
    check(apk.size in 1..MAX_APK_BYTES && checksum.size in 1..65_536) { "发布文件大小无效" }
    listOf(apk, checksum).forEach { asset ->
        val url = asset.url.toHttpUrlOrNull()
        check(url != null && url.scheme == "https" && url.host == "github.com" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
            url.pathSegments == listOf("guhh110", "Joyfin", "releases", "download", tag, asset.name)) {
            "发布文件地址不属于 Joyfin 官方仓库"
        }
    }
    return AppRelease(tag.removePrefix("v"), body.orEmpty().take(24_000), name, apk.url, apk.size, checksum.url)
}

internal const val MAX_APK_BYTES = 512L * 1024 * 1024

internal fun releaseChecksum(text: String, name: String): String {
    val values = text.lineSequence().mapNotNull { line ->
        Regex("^([0-9a-fA-F]{64})[ \\t]+\\*?(.+)$").matchEntire(line.trim())?.let {
            if (it.groupValues[2] == name) it.groupValues[1].lowercase() else null
        }
    }.toList()
    return values.singleOrNull() ?: error("安装包校验文件无效，请稍后重试")
}
