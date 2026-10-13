package dev.joyfin.playback

import dev.joyfin.data.DirectPlayProfile

/** Decoder availability alone does not imply the progressive extractor can read that codec. */
object ContainerPlaybackCapabilities {
    private data class Family(
        val containers: String,
        val video: Set<String>,
        val audio: Set<String>?,
    )

    private val allVideo = setOf("h264", "hevc", "vp8", "vp9", "av1", "mpeg2video", "mpeg4", "h263")
    private val families =
        listOf(
            Family("mp4,m4v,mov,3gp", allVideo, null),
            Family(
                "mkv,matroska,webm",
                allVideo - "h263",
                setOf(
                    "pcm",
                    "lpcm",
                    "pcm_s16le",
                    "pcm_s24le",
                    "pcm_s32le",
                    "pcm_f32le",
                    "pcm_u8",
                    "aac",
                    "mp2",
                    "mp3",
                    "ac3",
                    "eac3",
                    "dts",
                    "dca",
                    "dtshd",
                    "dts_hd",
                    "truehd",
                    "mlp",
                    "flac",
                    "alac",
                    "opus",
                    "vorbis",
                ),
            ),
            Family(
                "ts,mpegts,m2ts",
                setOf("h264", "hevc", "mpeg2video", "mpeg4"),
                setOf(
                    "aac",
                    "mp1",
                    "mp2",
                    "mp3",
                    "ac3",
                    "eac3",
                    "ac4",
                    "dts",
                    "dca",
                    "dtshd",
                    "dts_hd",
                ),
            ),
            Family("mpeg,mpg", setOf("mpeg2video"), setOf("ac3", "mp1", "mp2", "mp3")),
            Family("flv", setOf("h264"), setOf("aac", "mp3", "pcm_alaw", "pcm_mulaw")),
        )

    fun profiles(video: Set<String>, audio: Set<String>): List<DirectPlayProfile> =
        families.mapNotNull { family ->
            val supportedVideo = video.intersect(family.video)
            val supportedAudio = family.audio?.let { audio.intersect(it) } ?: audio
            // An empty codec string means ANY codec to Jellyfin, not "none supported".
            if (supportedVideo.isEmpty() || supportedAudio.isEmpty()) null
            else
                DirectPlayProfile(
                    container = family.containers,
                    videoCodec = supportedVideo.joinToString(","),
                    audioCodec = supportedAudio.joinToString(","),
                )
        }
}
