package dev.joyfin.data

import retrofit2.Response
import retrofit2.http.*

/** Retrofit owns HTTP execution, coroutine cancellation and generated JSON conversion. */
internal interface JellyfinService {
    @GET("QuickConnect/Enabled")
    suspend fun quickConnectEnabled(@Header("Authorization") auth: String): Response<Boolean>

    @POST("QuickConnect/Initiate")
    suspend fun initiateQuickConnect(@Header("Authorization") auth: String): Response<QuickConnectResult>

    @GET("QuickConnect/Connect")
    suspend fun pollQuickConnect(@Header("Authorization") auth: String,
        @Query("secret") secret: String): Response<QuickConnectResult>

    @POST("Users/AuthenticateWithQuickConnect")
    suspend fun authenticateQuickConnect(@Header("Authorization") auth: String,
        @Body body: QuickConnectRequest): Response<LoginResponse>

    @GET("Items")
    suspend fun auditItems(@Header("Authorization") auth: String, @QueryMap query: Map<String, String>): Response<AuditPageDto>
    @GET("Items/{id}")
    suspend fun auditItem(@Header("Authorization") auth: String, @Path("id") id: String,
        @Query("UserId") user: String, @Query("Fields") fields: String): Response<AuditItemDto>
    @GET("Shows/{id}/Episodes")
    suspend fun auditEpisodes(@Header("Authorization") auth: String, @Path("id") id: String,
        @QueryMap query: Map<String, String>): Response<AuditPageDto>
    @GET("MediaSegments/{id}")
    suspend fun segments(@Header("Authorization") auth: String, @Path("id") id: String): Response<MediaSegmentsResponse>
    @GET("Videos/{id}/{source}/Subtitles/{index}/Stream.{format}")
    suspend fun subtitleContent(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Path("source") source: String,
        @Path("index") index: Int,
        @Path("format") format: String,
        @Query("CopyTimestamps") copyTimestamps: Boolean = true,
    ): Response<okhttp3.ResponseBody>
    @DELETE("Videos/{id}/Subtitles/{index}")
    suspend fun deleteSubtitle(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Path("index") index: Int,
    ): Response<Unit>
    @POST("Videos/{id}/Subtitles")
    suspend fun uploadSubtitle(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: UploadSubtitleRequest,
    ): Response<Unit>
    @Headers("Accept: application/json")
    @GET("System/Info/Public")
    suspend fun publicInfo(@Header("Authorization") auth: String): Response<ServerInfo>

    @Headers("Accept: application/json")
    @POST("Users/AuthenticateByName")
    suspend fun login(
        @Header("Authorization") auth: String,
        @Body body: LoginRequest,
    ): Response<LoginResponse>

    @Headers("Accept: application/json")
    @GET("Users/{id}")
    suspend fun validate(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
    ): Response<UserDto>

    @Headers("Accept: application/json")
    @POST("Sessions/Logout")
    suspend fun logout(
        @Header("Authorization") auth: String,
        @Body body: Map<String, String> = emptyMap(),
    ): Response<Unit>

    @Headers("Accept: application/json")
    @GET("UserItems/Resume")
    suspend fun resume(
        @Header("Authorization") auth: String,
        @QueryMap query: Map<String, String>,
    ): Response<ItemsResponse>

    @Headers("Accept: application/json")
    @GET("UserViews")
    suspend fun views(
        @Header("Authorization") auth: String,
        @Query("UserId") user: String,
    ): Response<ItemsResponse>

    @Headers("Accept: application/json")
    @GET("Items/Latest")
    suspend fun latest(
        @Header("Authorization") auth: String,
        @QueryMap query: Map<String, String>,
    ): Response<List<ItemDto>>

    @Headers("Accept: application/json")
    @GET("Items")
    suspend fun items(
        @Header("Authorization") auth: String,
        @QueryMap query: Map<String, String>,
    ): Response<ItemsResponse>

    @Headers("Accept: application/json")
    @GET("Items/{id}")
    suspend fun item(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Query("UserId") user: String,
    ): Response<ItemDto>

    @Headers("Accept: application/json")
    @GET("Shows/{id}/Seasons")
    suspend fun seasons(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @QueryMap query: Map<String, String>,
    ): Response<ItemsResponse>

    @Headers("Accept: application/json")
    @GET("Shows/{id}/Episodes")
    suspend fun episodes(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @QueryMap query: Map<String, String>,
    ): Response<ItemsResponse>

    @Headers("Accept: application/json")
    @GET("Items/{id}/Similar")
    suspend fun similar(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @QueryMap query: Map<String, String>,
    ): Response<ItemsResponse>

    @Headers("Accept: application/json")
    @GET("Persons/{name}")
    suspend fun person(
        @Header("Authorization") auth: String,
        @Path("name") name: String,
        @Query("UserId") user: String,
    ): Response<ItemDto>

    @Headers("Accept: application/json")
    @POST("UserFavoriteItems/{id}")
    suspend fun favorite(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Query("UserId") user: String,
        @Body body: Map<String, String> = emptyMap(),
    ): Response<Unit>

    @Headers("Accept: application/json")
    @DELETE("UserFavoriteItems/{id}")
    suspend fun unfavorite(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Query("UserId") user: String,
    ): Response<Unit>

    @Headers("Accept: application/json")
    @POST("Users/{userId}/PlayedItems/{id}")
    suspend fun markPlayed(
        @Header("Authorization") auth: String,
        @Path("userId") user: String,
        @Path("id") id: String,
        @Body body: Map<String, String> = emptyMap(),
    ): Response<Unit>

    @Headers("Accept: application/json")
    @DELETE("Users/{userId}/PlayedItems/{id}")
    suspend fun markUnplayed(
        @Header("Authorization") auth: String,
        @Path("userId") user: String,
        @Path("id") id: String,
    ): Response<Unit>

    @Headers("Accept: application/json")
    @DELETE("Users/{userId}/PlayingItems/{id}")
    suspend fun removeResume(
        @Header("Authorization") auth: String,
        @Path("userId") user: String,
        @Path("id") id: String,
    ): Response<Unit>

    @Headers("Accept: application/json")
    @POST("Items/{id}/PlaybackInfo")
    suspend fun playback(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Query("UserId") user: String,
        @Query("IsPlayback") isPlayback: Boolean,
        @Body body: PlaybackRequest,
    ): Response<PlaybackResponse>

    @Headers("Accept: application/json")
    @POST("Sessions/Playing")
    suspend fun started(
        @Header("Authorization") auth: String,
        @Body body: PlaybackReport,
    ): Response<Unit>

    @Headers("Accept: application/json")
    @POST("Sessions/Playing/Progress")
    suspend fun progress(
        @Header("Authorization") auth: String,
        @Body body: PlaybackReport,
    ): Response<Unit>

    @Headers("Accept: application/json")
    @POST("Sessions/Playing/Stopped")
    suspend fun stopped(
        @Header("Authorization") auth: String,
        @Body body: PlaybackReport,
    ): Response<Unit>

    @Headers("Accept: application/json")
    @DELETE("Videos/ActiveEncodings")
    suspend fun stopEncoding(
        @Header("Authorization") auth: String,
        @Query("DeviceId") device: String,
        @Query("PlaySessionId") session: String,
    ): Response<Unit>
}
