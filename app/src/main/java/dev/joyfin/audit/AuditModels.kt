package dev.joyfin.audit

import dev.joyfin.data.Media
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.security.MessageDigest

@Serializable enum class AuditCategory(val label: String) {
    EPISODES("缺集"), VERSIONS("版本"), SUBTITLES("字幕"), METADATA("信息"),
}
@Serializable enum class AuditConfidence(val label: String) {
    SERVER("服务器标记"), OBSERVED("已读取的信息"), REVIEW("需人工确认"), UNKNOWN("信息不足"),
}
@Serializable data class AuditScope(val id: String? = null, val title: String = "全部电影与剧集")
@Serializable data class AuditTarget(
    val id: String, val name: String, val type: String,
    val seriesId: String? = null, val season: Int? = null, val episode: Int? = null,
) {
    fun media() = Media(id, name, type, seriesId = seriesId.orEmpty(), season = season ?: 0, episode = episode ?: 0)
}
@Serializable data class AuditFinding(
    val code: String,
    val category: AuditCategory,
    val target: AuditTarget,
    val title: String,
    val evidence: List<String>,
    val confidence: AuditConfidence,
    val sourceId: String? = null,
    val relatedIds: List<String> = listOf(target.id),
) {
    @Transient val key: String = auditDigest(listOf(code, target.id, sourceId.orEmpty()).joinToString("\u0000"))
    @Transient val fingerprint: String = auditDigest(listOf(key, title, confidence.name,
        relatedIds.sorted().joinToString("\u0000"), evidence.joinToString("\u0000")).joinToString("\u0001"))
}
@Serializable enum class AuditStatus(val label: String) {
    READY("尚未扫描"), RUNNING("扫描中"), COMPLETE("扫描完成"), PARTIAL("扫描未完成"), CANCELED("已取消"),
}
@Serializable data class AuditReport(
    val scope: AuditScope = AuditScope(), val status: AuditStatus = AuditStatus.READY,
    val startedAt: Long = 0, val finishedAt: Long = 0, val checked: Int = 0,
    val totalWorks: Int? = null, val processedWorks: Int = 0,
    val findings: List<AuditFinding> = emptyList(), val warnings: List<String> = emptyList(),
    val futureEpisodes: Int = 0, val rulesVersion: Int = 1,
)
@Serializable data class AuditSaved(val report: AuditReport, val ignored: Set<String> = emptySet())

fun auditDigest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
