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
    suspend fun getGenreMappings(): Response<GenreMappingsResponse>

    // Favorites
    @GET("api/audiobooks/favorites")
    suspend fun getFavorites(): Response<List<Audiobook>>

    @POST("api/audiobooks/{id}/favorite/toggle")
    suspend fun toggleFavorite(@Path("id") audiobookId: Int): Response<FavoriteResponse>

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

    @GET("api/profile/stats")
    suspend fun getProfileStats(): Response<UserStats>

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

    // Health/Version
    @GET("api/health")
    suspend fun getHealth(): Response<HealthResponse>

    // Admin - Server Settings
    @GET("api/settings/all")
    suspend fun getServerSettings(): Response<ServerSettingsResponse>

    @PUT("api/settings/all")
    suspend fun updateServerSettings(@Body settings: ServerSettingsUpdate): Response<Unit>

    // Admin - AI Settings
    @GET("api/settings/ai")
    suspend fun getAiSettings(): Response<AiSettingsResponse>

    @PUT("api/settings/ai")
    suspend fun updateAiSettings(@Body settings: AiSettingsUpdate): Response<Unit>

    @POST("api/settings/ai/test")
    suspend fun testAiConnection(@Body settings: AiSettingsUpdate): Response<AiTestResponse>

    // Admin - User Management
    @GET("api/users")
    suspend fun getUsers(): Response<List<UserInfo>>

    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<UserInfo>

    @PUT("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body request: UpdateUserRequest): Response<UserInfo>

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): Response<Unit>

    // Admin - Maintenance
    @POST("api/maintenance/force-rescan")
    suspend fun forceRescan(): Response<ScanResult>

    // Series Recap (Catch Me Up)
    @GET("api/settings/ai/status")
    suspend fun getAiStatus(): Response<AiStatusResponse>

    @GET("api/series/{seriesName}/recap")
    suspend fun getSeriesRecap(@Path("seriesName", encoded = true) seriesName: String): Response<SeriesRecapResponse>

    @DELETE("api/series/{seriesName}/recap")
    suspend fun clearSeriesRecap(@Path("seriesName", encoded = true) seriesName: String): Response<Unit>
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

data class HealthResponse(
    val status: String,
    val message: String,
    val version: String?
)

data class AiStatusResponse(
    val configured: Boolean,
    val provider: String?
)

data class SeriesRecapResponse(
    val recap: String,
    val cached: Boolean,
    val cachedAt: String?,
    val booksIncluded: List<RecapBookInfo>
)

data class RecapBookInfo(
    val id: Int,
    val title: String,
    val position: Float?
)

// Admin Settings Data Classes
data class ServerSettingsResponse(
    val settings: ServerSettings,
    val lockedFields: List<String>?
)

data class ServerSettings(
    val port: String?,
    val nodeEnv: String?,
    val databasePath: String?,
    val dataDir: String?,
    val audiobooksDir: String?,
    val uploadDir: String?,
    val libraryScanInterval: Int?
)

data class ServerSettingsUpdate(
    val port: String? = null,
    val nodeEnv: String? = null,
    val databasePath: String? = null,
    val dataDir: String? = null,
    val audiobooksDir: String? = null,
    val uploadDir: String? = null,
    val libraryScanInterval: Int? = null
)

data class AiSettingsResponse(
    val settings: AiSettings
)

data class AiSettings(
    val aiProvider: String?,
    val openaiApiKey: String?,
    val openaiModel: String?,
    val geminiApiKey: String?,
    val geminiModel: String?,
    val recapCustomPrompt: String?,
    val recapOffensiveMode: Boolean?,
    val recapDefaultPrompt: String?
)

data class AiSettingsUpdate(
    val aiProvider: String? = null,
    val openaiApiKey: String? = null,
    val openaiModel: String? = null,
    val geminiApiKey: String? = null,
    val geminiModel: String? = null,
    val recapCustomPrompt: String? = null,
    val recapOffensiveMode: Boolean? = null
)

data class AiTestResponse(
    val message: String?,
    val response: String?,
    val error: String?
)

data class FavoriteResponse(
    val success: Boolean,
    @com.google.gson.annotations.SerializedName("is_favorite")
    val isFavorite: Boolean
)

data class UserInfo(
    val id: Int,
    val username: String,
    val email: String?,
    @com.google.gson.annotations.SerializedName("is_admin")
    val isAdmin: Int,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String?
)

data class CreateUserRequest(
    val username: String,
    val password: String,
    val email: String? = null,
    @com.google.gson.annotations.SerializedName("is_admin")
    val isAdmin: Boolean = false
)

data class UpdateUserRequest(
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    @com.google.gson.annotations.SerializedName("is_admin")
    val isAdmin: Boolean? = null
)
