package com.sappho.audiobooks.data.remote

import com.sappho.audiobooks.domain.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SapphoApi {

    // Authentication
    @POST("api/auth/login")
    suspend fun login(
        @Body credentials: LoginRequest
    ): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(
        @Body credentials: RegisterRequest
    ): Response<AuthResponse>

    // Audiobooks
    @GET("api/audiobooks")
    suspend fun getAudiobooks(
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<AudiobooksResponse>

    @GET("api/audiobooks/{id}")
    suspend fun getAudiobook(@Path("id") id: Int): Response<Audiobook>

    @GET("api/audiobooks/meta/recent")
    suspend fun getRecentlyAdded(@Query("limit") limit: Int = 10): Response<List<Audiobook>>

    @GET("api/audiobooks/meta/in-progress")
    suspend fun getInProgress(@Query("limit") limit: Int = 10): Response<List<Audiobook>>

    @GET("api/audiobooks/meta/finished")
    suspend fun getFinished(@Query("limit") limit: Int = 10): Response<List<Audiobook>>

    @GET("api/audiobooks/meta/up-next")
    suspend fun getUpNext(@Query("limit") limit: Int = 10): Response<List<Audiobook>>

    @GET("api/audiobooks/meta/genres")
    suspend fun getGenres(): Response<List<GenreInfo>>

    @GET("api/audiobooks/meta/genre-mappings")
    suspend fun getGenreMappings(): Response<Map<String, List<String>>>

    // Progress
    @GET("api/audiobooks/{id}/progress")
    suspend fun getProgress(@Path("id") audiobookId: Int): Response<Progress>

    @POST("api/audiobooks/{id}/progress")
    suspend fun updateProgress(
        @Path("id") audiobookId: Int,
        @Body request: ProgressUpdateRequest
    ): Response<Unit>

    // Mark Finished - sends position: 0, completed: 1
    @POST("api/audiobooks/{id}/progress")
    suspend fun markFinished(
        @Path("id") audiobookId: Int,
        @Body request: ProgressUpdateRequest = ProgressUpdateRequest(0, 1, "stopped")
    ): Response<Unit>

    // Clear Progress - sends position: 0, completed: 0
    @POST("api/audiobooks/{id}/progress")
    suspend fun clearProgress(
        @Path("id") audiobookId: Int,
        @Body request: ProgressUpdateRequest = ProgressUpdateRequest(0, 0, "stopped")
    ): Response<Unit>

    // Chapters
    @GET("api/audiobooks/{id}/chapters")
    suspend fun getChapters(@Path("id") audiobookId: Int): Response<List<Chapter>>

    // Files
    @GET("api/audiobooks/{id}/files")
    suspend fun getFiles(@Path("id") audiobookId: Int): Response<List<AudiobookFile>>

    // Delete
    @DELETE("api/audiobooks/{id}")
    suspend fun deleteAudiobook(@Path("id") audiobookId: Int): Response<Unit>

    // User Profile
    @GET("api/profile")
    suspend fun getProfile(): Response<User>

    @PUT("api/profile")
    suspend fun updateProfile(@Body request: ProfileUpdateRequest): Response<User>

    @PUT("api/profile/password")
    suspend fun updatePassword(@Body request: PasswordUpdateRequest): Response<Unit>

    @Multipart
    @PUT("api/profile")
    suspend fun updateProfileWithAvatar(
        @Part("displayName") displayName: okhttp3.RequestBody?,
        @Part("email") email: okhttp3.RequestBody?,
        @Part avatar: okhttp3.MultipartBody.Part?
    ): Response<User>

    @DELETE("api/profile/avatar")
    suspend fun deleteAvatar(): Response<Unit>

    // Library Settings (Admin only)
    @GET("api/settings/library")
    suspend fun getLibrarySettings(): Response<LibrarySettings>

    @PUT("api/settings/library")
    suspend fun updateLibrarySettings(@Body settings: LibrarySettings): Response<LibrarySettings>

    @POST("api/library/scan")
    suspend fun scanLibrary(@Query("refresh") refresh: Boolean = false): Response<ScanResult>

    @POST("api/library/force-rescan")
    suspend fun forceRescanLibrary(): Response<ScanResult>
}

data class ProfileUpdateRequest(
    val displayName: String?,
    val email: String?
)

data class PasswordUpdateRequest(
    val currentPassword: String,
    val newPassword: String
)

data class LibrarySettings(
    val libraryPath: String,
    val uploadPath: String
)

data class ScanResult(
    val message: String?,
    val stats: ScanStats
)

data class ScanStats(
    val imported: Int,
    val skipped: Int,
    val errors: Int,
    val scanning: Boolean?,
    val metadataRefreshed: Int?,
    val metadataErrors: Int?,
    val totalFiles: Int?
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String? = null
)

data class UpdateProgressRequest(
    val audiobookId: Int,
    val position: Int,
    val currentChapter: Int? = null
)

data class ProgressUpdateRequest(
    val position: Int,
    val completed: Int,
    val state: String = "stopped"
)
