package dev.joyfin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Jellyfin wire models. UI and business code consume typed fields, never JSON objects. */
@Serializable data class ServerInfo(@SerialName("ServerName") val serverName: String = "Jellyfin")

@Serializable
data class UserDto(@SerialName("Id") val id: String, @SerialName("Name") val name: String = "")

@Serializable
data class LoginRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String,
)

@Serializable
data class LoginResponse(
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("User") val user: UserDto,
)

@Serializable
data class UserDataDto(
    @SerialName("PlaybackPositionTicks") val positionTicks: Long = 0,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val favorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedCount: Int? = null,
)

@Serializable
data class PersonDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Role") val role: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("PrimaryImageTag") val primaryImageTag: String = "",
)

@Serializable
data class MediaStream(
    @SerialName("Type") val type: String = "",
    @SerialName("Index") val index: Int = -1,
    @SerialName("Codec") val codec: String = "",
    @SerialName("Language") val language: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("DisplayTitle") val displayTitle: String = "",
    @SerialName("DeliveryMethod") val deliveryMethod: String = "",
    @SerialName("IsForced") val forced: Boolean = false,
    @SerialName("IsHearingImpaired") val hearingImpaired: Boolean = false,
    @SerialName("IsCommentary") val commentary: Boolean = false,
    @SerialName("Channels") val channels: Int = 0,
    @SerialName("SampleRate") val sampleRate: Int = 48000,
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    @SerialName("BitDepth") val bitDepth: Int = 8,
    @SerialName("VideoRange") val videoRange: String = "",
    @SerialName("VideoRangeType") val videoRangeType: String = "",
)

@Serializable
data class MediaSource(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Container") val container: String = "",
    @SerialName("Bitrate") val bitrate: Long = 0,
    @SerialName("Size") val size: Long = 0,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("DirectStreamUrl") val directStreamUrl: String? = null,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("DefaultAudioStreamIndex") val defaultAudioIndex: Int = -1,
    @SerialName("MediaStreams") val streams: List<MediaStream> = emptyList(),
)

@Serializable
data class ItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("Overview") val overview: String = "",
    @SerialName("ProductionYear") val year: Int = 0,
    @SerialName("RunTimeTicks") val ticks: Long = 0,
    @SerialName("UserData") val userData: UserDataDto = UserDataDto(),
    @SerialName("ImageTags") val imageTags: Map<String, String?> = emptyMap(),
    @SerialName("ParentPrimaryImageItemId") val parentPrimaryImageId: String = "",
    @SerialName("ParentPrimaryImageTag") val parentPrimaryImageTag: String = "",
    @SerialName("SeriesPrimaryImageTag") val seriesImageTag: String = "",
    @SerialName("SeriesId") val seriesId: String = "",
    @SerialName("ParentIndexNumber") val season: Int = 0,
    @SerialName("IndexNumber") val episode: Int = 0,
    @SerialName("MediaSources") val sources: List<MediaSource> = emptyList(),
    @SerialName("BackdropImageTags") val backdropTags: List<String> = emptyList(),
    @SerialName("ParentBackdropItemId") val parentBackdropId: String = "",
    @SerialName("ParentBackdropImageTags") val parentBackdropTags: List<String> = emptyList(),
    @SerialName("LogoImageTag") val logoImageTag: String = "",
    @SerialName("ParentLogoItemId") val parentLogoId: String = "",
    @SerialName("ParentLogoImageTag") val parentLogoTag: String = "",
    @SerialName("People") val people: List<PersonDto> = emptyList(),
    @SerialName("Genres") val genres: List<String?> = emptyList(),
    @SerialName("CommunityRating") val rating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String = "",
    @SerialName("PremiereDate") val premiereDate: String = "",
    @SerialName("SeasonId") val seasonId: String = "",
    @SerialName("SeriesName") val seriesName: String = "",
    @SerialName("ChildCount") val childCount: Int = 0,
    @SerialName("ProductionLocations") val locations: List<String?> = emptyList(),
    @SerialName("OriginalLanguage") val language: String = "",
    @SerialName("CollectionType") val collectionType: String = "",
    @SerialName("ParentId") val parentId: String = "",
)

@Serializable
data class ItemsResponse(
    @SerialName("Items") val items: List<ItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val total: Int = 0,
)

@Serializable
data class PlaybackResponse(
    @SerialName("ErrorCode") val errorCode: String? = null,
    @SerialName("MediaSources") val sources: List<MediaSource> = emptyList(),
    @SerialName("PlaySessionId") val sessionId: String = "",
)

@Serializable
data class ProfileCondition(
    @SerialName("Condition") val condition: String = "LessThanEqual",
    @SerialName("Property") val property: String,
    @SerialName("Value") val value: String,
    @SerialName("IsRequired") val required: Boolean = false,
)

@Serializable
data class CodecProfile(
    @SerialName("Type") val type: String,
    @SerialName("Codec") val codec: String,
    @SerialName("Conditions") val conditions: List<ProfileCondition> = emptyList(),
)

@Serializable
data class DirectPlayProfile(
    @SerialName("Type") val type: String = "Video",
    @SerialName("Container") val container: String,
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String,
)

@Serializable
data class TranscodingProfile(
    @SerialName("Type") val type: String = "Video",
    @SerialName("Container") val container: String = "ts",
    @SerialName("Protocol") val protocol: String = "hls",
    @SerialName("Context") val context: String = "Streaming",
    @SerialName("VideoCodec") val videoCodec: String = "h264",
    @SerialName("AudioCodec") val audioCodec: String = "aac",
    @SerialName("MaxAudioChannels") val maxAudioChannels: String = "6",
    @SerialName("MinSegments") val minSegments: Int = 2,
    @SerialName("SegmentLength") val segmentLength: Int = 4,
    @SerialName("EnableSubtitlesInManifest") val enableSubtitlesInManifest: Boolean = false,
    @SerialName("BreakOnNonKeyFrames") val breakOnNonKeyFrames: Boolean = false,
)

@Serializable
data class SubtitleProfile(
    @SerialName("Format") val format: String,
    @SerialName("Method") val method: String,
)

@Serializable
data class DeviceProfileDto(
    @SerialName("Name") val name: String = "Joyfin Android Cinema",
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int = 80000000,
    @SerialName("MaxStaticBitrate") val maxStaticBitrate: Int = 80000000,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfile> = emptyList(),
    @SerialName("TranscodingProfiles")
    val transcodingProfiles: List<TranscodingProfile> = emptyList(),
    @SerialName("CodecProfiles") val codecProfiles: List<CodecProfile> = emptyList(),
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<SubtitleProfile> = emptyList(),
)

@Serializable
data class PlaybackRequest(
    @SerialName("UserId") val userId: String,
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfileDto,
    @SerialName("StartTimeTicks") val startTimeTicks: Long,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int,
    @SerialName("MaxAudioChannels") val maxAudioChannels: Int,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean = true,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int,
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
)

@Serializable
data class PlaybackReport(
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("MediaSourceId") val mediaSourceId: String = "",
    @SerialName("PlaySessionId") val playSessionId: String = "",
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("IsPaused") val isPaused: Boolean? = null,
    @SerialName("CanSeek") val canSeek: Boolean? = null,
    @SerialName("PlayMethod") val playMethod: String? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)
