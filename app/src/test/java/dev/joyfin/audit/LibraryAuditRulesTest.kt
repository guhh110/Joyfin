package dev.joyfin.audit

import dev.joyfin.data.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LibraryAuditRulesTest {
    private val today = LocalDate.of(2026, 10, 23)
    private val series = AuditItemDto("series", "剧", "Series", "简介", 2026, images = mapOf("Primary" to "tag"))
    private val video = AuditStreamDto("Video", "hevc", width = 1920, height = 1080)
    private fun source(id: String = "v1", subtitles: Boolean = false) = AuditSourceDto(id, id, "mkv", 1000, 100,
        listOf(video) + if (subtitles) listOf(AuditStreamDto("Subtitle", "ass", "zh")) else emptyList())
    private fun movie(vararg sources: AuditSourceDto) = AuditItemDto("movie", "电影", "Movie", "简介", 2026,
        ticks = 100, images = mapOf("Primary" to "tag"), sources = sources.toList())
    private fun episode(number: Int, end: Int? = null, season: Int = 1, id: String = "$season-$number") =
        AuditItemDto(id, "第 $number 集", "Episode", season = season, episode = number, episodeEnd = end, seriesId = series.id)
    private fun subtitles(item: AuditItemDto, local: (String, String) -> Boolean? = { _, _ -> false }) =
        LibraryAuditRules.item(item, local).filter { it.category == AuditCategory.SUBTITLES }
    private fun gaps(actual: List<AuditItemDto>, missing: List<AuditItemDto> = emptyList()) =
        LibraryAuditRules.episodes(series, actual, missing, today)

    @Test fun unknownStreamsAreNotReportedAsConfirmedSubtitleAbsence() {
        val unknown = subtitles(movie(source().copy(streams = null)))
        assertEquals(AuditConfidence.UNKNOWN, unknown.single().confidence)
        assertFalse(unknown.single().title.contains("未发现可选字幕"))
        assertEquals(AuditConfidence.OBSERVED, subtitles(movie(source())).single().confidence)
        assertTrue(subtitles(movie(source())).single().evidence.any { it.contains("自带") })
    }

    @Test fun localSubtitlesAreMatchedToTheExactItemAndVersion() {
        val checked = mutableListOf<Pair<String, String>>()
        val results = subtitles(movie(source("v1"), source("v2"))) { item, version ->
            checked += item to version; item == "movie" && version == "v1"
        }
        assertEquals("v2", results.single().sourceId)
        assertEquals(listOf("movie" to "v1", "movie" to "v2"), checked)
    }

    @Test fun eitherServerOrLocalSubtitlesAreEnoughAndUnknownLocalIndexStaysUnknown() {
        assertTrue(subtitles(movie(source(subtitles = true))) { _, _ -> null }.isEmpty())
        assertTrue(subtitles(movie(source().copy(streams = null))) { _, _ -> true }.isEmpty())
        assertEquals(AuditConfidence.UNKNOWN, subtitles(movie(source())) { _, _ -> null }.single().confidence)
    }

    @Test fun missingVersionIdentityCannotBeBoundToAnArbitraryLocalFile() {
        var calls = 0
        val result = subtitles(movie(source().copy(id = null))) { _, _ -> calls++; true }
        assertEquals(0, calls)
        assertEquals(AuditConfidence.UNKNOWN, result.single().confidence)
    }

    @Test fun missingSourcesAndExplicitlyEmptySourcesDoNotMeanNoSubtitlesOrBadFiles() {
        for (sources in listOf<List<AuditSourceDto>?>(null, emptyList())) {
            val rows = LibraryAuditRules.item(movie().copy(sources = sources)) { _, _ -> false }
            assertTrue(rows.none { it.category == AuditCategory.SUBTITLES })
            assertTrue(rows.any { it.code == "sources-unknown" && it.confidence == AuditConfidence.UNKNOWN })
        }
    }

    @Test fun normalDifferentQualityVersionsAreInformationalRatherThanDuplicateFiles() {
        val rows = LibraryAuditRules.item(movie(source(), source("4k").copy(streams = listOf(video.copy(width = 3840, height = 2160))))) { _, _ -> false }
        assertTrue(rows.any { it.code == "multiple-versions" && it.confidence == AuditConfidence.OBSERVED })
        assertTrue(rows.none { it.code == "similar-versions" })
        assertTrue(LibraryAuditRules.item(movie(source(), source("copy"))) { _, _ -> false }
            .any { it.code == "similar-versions" && it.confidence == AuditConfidence.REVIEW })
    }

    @Test fun combinedEpisodesAndOverlappingRangesCoverAllIncludedNumbers() {
        assertTrue(gaps(listOf(episode(1, 2), episode(3))).isEmpty())
        assertTrue(gaps(listOf(episode(1, 3), episode(2, 4), episode(5))).isEmpty())
        assertEquals("gap:1:2:3", gaps(listOf(episode(1), episode(4))).single().code)
    }

    @Test fun partialSeasonsDoNotInventLeadingOrTrailingEpisodesAndSpecialsAreNotSequential() {
        assertTrue(gaps(listOf(episode(3), episode(4))).isEmpty())
        assertTrue(gaps(listOf(episode(1), episode(6, id = "special", season = 0))).isEmpty())
        assertTrue(gaps(listOf(episode(1, season = 0), episode(3, season = 0))).isEmpty())
    }

    @Test fun alreadyPresentRangesAreSubtractedFromServerMissingRecords() {
        assertTrue(gaps(listOf(episode(1), episode(2)), listOf(episode(1, 2))).isEmpty())
        val result = gaps(listOf(episode(1), episode(3)), listOf(episode(1, 3).copy(premiere = "2020-01-01")))
        assertEquals("server-missing:1:2:2", result.single().code)
        assertEquals(AuditConfidence.SERVER, result.single().confidence)
    }

    @Test fun aServerMarkedEpisodeDoesNotHideTheRestOfAnObservedGap() {
        val result = gaps(listOf(episode(1), episode(6)), listOf(episode(3)))
        assertEquals(setOf("server-missing:1:3:3", "gap:1:2:2", "gap:1:4:5"), result.map { it.code }.toSet())
    }

    @Test fun futureEpisodesAreExcludedFromBothServerMissingAndGapInference() {
        val future = episode(2).copy(premiere = "2026-12-01T00:00:00Z")
        assertTrue(gaps(listOf(episode(1), episode(3)), listOf(future)).isEmpty())
        assertEquals(AuditConfidence.REVIEW, gaps(emptyList(), listOf(episode(2).copy(premiere = "bad-date"))).single().confidence)
    }

    @Test fun hugeGapsAreRepresentedAsIntervalsAndInvalidMergedRangesAreNotUsed() {
        assertEquals("gap:1:2:2147483646", gaps(listOf(episode(1), episode(Int.MAX_VALUE))).single().code)
        assertTrue(gaps(listOf(episode(5, 2), episode(6))).isEmpty())
        assertTrue(LibraryAuditRules.item(episode(5, 2)) { _, _ -> false }.any { it.evidence.any { line -> line.contains("结束编号") } })
    }

    @Test fun commonProviderIdentityMatchesEvenWhenOnlyOneEntryHasAnAdditionalProvider() {
        val one = movie(source()).copy(id = "one", providers = mapOf("Tmdb" to "12", "Imdb" to "tt1"))
        val two = one.copy(id = "two", providers = mapOf("imdb" to "tt1"))
        assertEquals(listOf("one", "two"), LibraryAuditRules.duplicates(listOf(one, two)).single().relatedIds)
        assertTrue(LibraryAuditRules.duplicates(listOf(one, two.copy(providers = mapOf("Tmdb" to "13")))).isEmpty())
    }

    @Test fun episodeIdentityIsScopedToItsSeriesAndUnknownNumbersAreNotCoercedToZero() {
        assertEquals(1, LibraryAuditRules.duplicates(listOf(episode(1), episode(1, id = "other"))).size)
        assertTrue(LibraryAuditRules.duplicates(listOf(episode(1), episode(1, id = "other").copy(seriesId = "another"))).isEmpty())
        assertTrue(LibraryAuditRules.duplicates(listOf(episode(1).copy(episode = null), episode(1, id = "other").copy(episode = null))).isEmpty())
    }

    @Test fun cachedFindingsRoundTripAndDataChangesInvalidateAnIgnoreFingerprint() {
        val finding = LibraryAuditRules.item(movie(source())) { _, _ -> false }.first()
        val saved = AuditSaved(AuditReport(findings = listOf(finding)), setOf(finding.fingerprint))
        val decoded = AppJson.decodeFromString<AuditSaved>(AppJson.encodeToString(saved))
        assertEquals(finding.key, decoded.report.findings.single().key)
        assertEquals(finding.fingerprint, decoded.report.findings.single().fingerprint)
        assertFalse(AppJson.encodeToString(saved).contains("fingerprint"))
        assertNotEquals(finding.fingerprint, finding.copy(evidence = finding.evidence + "新信息").fingerprint)
        assertNotEquals(finding.fingerprint, finding.copy(relatedIds = listOf("different")).fingerprint)
    }

    @Test fun auditDtoRetainsMissingVersusEmptyData() {
        val absent = AppJson.decodeFromString<AuditItemDto>("""{"Id":"one"}""")
        assertNull(absent.season); assertNull(absent.episode); assertNull(absent.sources)
        val explicit = AppJson.decodeFromString<AuditItemDto>("""{"Id":"one","ParentIndexNumber":0,"IndexNumber":0,"MediaSources":[]}""")
        assertEquals(0, explicit.season); assertEquals(0, explicit.episode); assertEquals(emptyList<AuditSourceDto>(), explicit.sources)
    }
}
