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

    // Ratings
    @GET("api/ratings/audiobook/{audiobookId}")
    suspend fun getUserRating(@Path("audiobookId") audiobookId: Int): Response<UserRating?>

    @GET("api/ratings/audiobook/{audiobookId}/average")
    suspend fun getAverageRating(@Path("audiobookId") audiobookId: Int): Response<AverageRating>

    @POST("api/ratings/audiobook/{audiobookId}")
    suspend fun setRating(
        @Path("audiobookId") audiobookId: Int,
        @Body request: RatingRequest
    ): Response<UserRating>

    @DELETE("api/ratings/audiobook/{audiobookId}")
    suspend fun deleteRating(@Path("audiobookId") audiobookId: Int): Response<Unit>

    // Audiobook Update (Admin)
    @PUT("api/audiobooks/{id}")
    suspend fun updateAudiobook(
        @Path("id") id: Int,
        @Body request: AudiobookUpdateRequest
    ): Response<com.sappho.audiobooks.domain.model.Audiobook>

    // Backup Endpoints
    @GET("api/backup")
    suspend fun getBackups(): Response<BackupsResponse>

    @POST("api/backup")
    suspend fun createBackup(): Response<BackupInfo>

    @GET("api/backup/{filename}")
    @Streaming
    suspend fun downloadBackup(@Path("filename") filename: String): Response<okhttp3.ResponseBody>

    @DELETE("api/backup/{filename}")
    suspend fun deleteBackup(@Path("filename") filename: String): Response<Unit>

    @POST("api/backup/restore/{filename}")
    suspend fun restoreBackup(@Path("filename") filename: String): Response<RestoreResponse>

    @GET("api/backup/retention")
    suspend fun getBackupRetention(): Response<BackupRetention>

    @PUT("api/backup/retention")
    suspend fun updateBackupRetention(@Body retention: BackupRetention): Response<BackupRetention>

    @Multipart
    @POST("api/backup/upload")
    suspend fun uploadBackup(@Part file: MultipartBody.Part): Response<BackupInfo>

    // Maintenance Endpoints
    @GET("api/maintenance/logs")
    suspend fun getLogs(
        @Query("lines") lines: Int = 100,
        @Query("level") level: String? = null
    ): Response<LogsResponse>

    @DELETE("api/maintenance/logs")
    suspend fun clearLogs(): Response<Unit>

    @GET("api/maintenance/statistics")
    suspend fun getLibraryStatistics(): Response<LibraryStatistics>

    @GET("api/maintenance/duplicates")
    suspend fun getDuplicates(): Response<DuplicatesResponse>

    @POST("api/maintenance/duplicates/merge")
    suspend fun mergeDuplicates(@Body request: MergeDuplicatesRequest): Response<MergeResult>

    @POST("api/maintenance/scan-library")
    suspend fun scanLibraryMaintenance(): Response<ScanResult>

    @POST("api/maintenance/clear-library")
    suspend fun clearLibrary(): Response<ClearLibraryResult>

    @GET("api/maintenance/jobs")
    suspend fun getJobs(): Response<JobsResponse>

    // Collections Endpoints
    @GET("api/collections")
    suspend fun getCollections(): Response<List<Collection>>

    @POST("api/collections")
    suspend fun createCollection(@Body request: CreateCollectionRequest): Response<Collection>

    @GET("api/collections/{id}")
    suspend fun getCollection(@Path("id") id: Int): Response<CollectionDetail>

    @PUT("api/collections/{id}")
    suspend fun updateCollection(
        @Path("id") id: Int,
        @Body request: UpdateCollectionRequest
    ): Response<Collection>

    @DELETE("api/collections/{id}")
    suspend fun deleteCollection(@Path("id") id: Int): Response<Unit>

    @POST("api/collections/{id}/items")
    suspend fun addToCollection(
        @Path("id") collectionId: Int,
        @Body request: AddToCollectionRequest
    ): Response<Unit>

    @DELETE("api/collections/{id}/items/{bookId}")
    suspend fun removeFromCollection(
        @Path("id") collectionId: Int,
        @Path("bookId") bookId: Int
    ): Response<Unit>

    @PUT("api/collections/{id}/items/reorder")
    suspend fun reorderCollection(
        @Path("id") collectionId: Int,
        @Body request: ReorderCollectionRequest
    ): Response<Unit>

    @GET("api/collections/for-book/{bookId}")
    suspend fun getCollectionsForBook(@Path("bookId") bookId: Int): Response<List<CollectionForBook>>

    // Upload Endpoints
    @Multipart
    @POST("api/upload")
    suspend fun uploadAudiobook(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody?,
        @Part("author") author: RequestBody?
    ): Response<UploadResponse>
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

// Rating Data Classes
data class UserRating(
    val id: Int,
    @com.google.gson.annotations.SerializedName("user_id")
    val userId: Int,
    @com.google.gson.annotations.SerializedName("audiobook_id")
    val audiobookId: Int,
    val rating: Int?,
    val review: String?,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String?,
    @com.google.gson.annotations.SerializedName("updated_at")
    val updatedAt: String?
)

data class AverageRating(
    val average: Float?,
    val count: Int
)

data class RatingRequest(
    val rating: Int?,
    val review: String? = null
)

// Audiobook Update Request
data class AudiobookUpdateRequest(
    val title: String? = null,
    val subtitle: String? = null,
    val author: String? = null,
    val narrator: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val tags: String? = null,
    val series: String? = null,
    @com.google.gson.annotations.SerializedName("series_position")
    val seriesPosition: Float? = null,
    @com.google.gson.annotations.SerializedName("published_year")
    val publishedYear: Int? = null,
    @com.google.gson.annotations.SerializedName("copyright_year")
    val copyrightYear: Int? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val asin: String? = null,
    val language: String? = null,
    val rating: Float? = null,
    val abridged: Boolean? = null,
    @com.google.gson.annotations.SerializedName("cover_url")
    val coverUrl: String? = null
)

// Backup Data Classes
data class BackupsResponse(
    val backups: List<BackupInfo>,
    val status: BackupStatus?
)

data class BackupStatus(
    @com.google.gson.annotations.SerializedName("is_running")
    val isRunning: Boolean?,
    @com.google.gson.annotations.SerializedName("current_operation")
    val currentOperation: String?,
    val progress: Int?
)

data class BackupInfo(
    val filename: String,
    val size: Long,
    val created: String?,
    val sizeFormatted: String?,
    val createdFormatted: String?
)

data class RestoreResponse(
    val success: Boolean,
    val message: String?
)

data class BackupRetention(
    @com.google.gson.annotations.SerializedName("max_backups")
    val maxBackups: Int,
    @com.google.gson.annotations.SerializedName("auto_backup")
    val autoBackup: Boolean?,
    @com.google.gson.annotations.SerializedName("backup_interval_days")
    val backupIntervalDays: Int?
)

// Maintenance Data Classes
data class LogsResponse(
    val logs: List<LogEntry>,
    val total: Int?
)

data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
    val source: String?
)

data class LibraryStatistics(
    val totals: StatTotals?,
    val byFormat: List<FormatStats>?,
    val topAuthors: List<AuthorStats>?,
    val topSeries: List<SeriesStats>?,
    val topNarrators: List<NarratorStats>?,
    val addedOverTime: List<MonthlyStats>?,
    val userStats: List<UserStatEntry>?
)

data class StatTotals(
    val books: Int,
    val size: Long,
    val duration: Long,
    val avgDuration: Double?
)

data class FormatStats(
    val format: String?,
    val count: Int,
    val size: Long
)

data class AuthorStats(
    val author: String?,
    val count: Int,
    val size: Long?,
    val duration: Long?
)

data class SeriesStats(
    val series: String?,
    val count: Int,
    val size: Long?,
    val duration: Long?
)

data class NarratorStats(
    val narrator: String?,
    val count: Int,
    val duration: Long?
)

data class MonthlyStats(
    val month: String?,
    val count: Int,
    val size: Long?
)

data class UserStatEntry(
    val username: String?,
    val booksStarted: Int?,
    val booksCompleted: Int?,
    val totalListenTime: Long?
)

data class DuplicatesResponse(
    val duplicateGroups: List<DuplicateGroup>?,
    val totalDuplicates: Int?
)

data class DuplicateGroup(
    val id: String,
    val matchReason: String?,
    val books: List<DuplicateBook>,
    val suggestedKeep: Int?
)

data class JobsResponse(
    val jobs: Map<String, JobInfo>,
    val forceRefreshInProgress: Boolean?
)

data class DuplicateBook(
    val id: Int,
    val title: String,
    val author: String?,
    @com.google.gson.annotations.SerializedName("file_path")
    val filePath: String?,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String?
)

data class MergeDuplicatesRequest(
    @com.google.gson.annotations.SerializedName("keep_id")
    val keepId: Int,
    @com.google.gson.annotations.SerializedName("delete_ids")
    val deleteIds: List<Int>
)

data class MergeResult(
    val success: Boolean,
    val message: String?,
    val deleted: Int?
)

data class ClearLibraryResult(
    val success: Boolean,
    val message: String?,
    val deleted: Int?
)

data class JobInfo(
    val id: String = "",  // Set from map key
    val name: String,
    val description: String?,
    val status: String,
    val interval: String?,
    @com.google.gson.annotations.SerializedName("lastRun")
    val lastRun: String?,
    @com.google.gson.annotations.SerializedName("nextRun")
    val nextRun: String?,
    val canTrigger: Boolean?
)

// Collections Data Classes
data class Collection(
    val id: Int,
    val name: String,
    val description: String?,
    @com.google.gson.annotations.SerializedName("user_id")
    val userId: Int,
    @com.google.gson.annotations.SerializedName("book_count")
    val bookCount: Int?,
    @com.google.gson.annotations.SerializedName("first_cover")
    val firstCover: String?,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String?,
    @com.google.gson.annotations.SerializedName("updated_at")
    val updatedAt: String?
)

data class CollectionDetail(
    val id: Int,
    val name: String,
    val description: String?,
    @com.google.gson.annotations.SerializedName("user_id")
    val userId: Int,
    val books: List<com.sappho.audiobooks.domain.model.Audiobook>,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String?,
    @com.google.gson.annotations.SerializedName("updated_at")
    val updatedAt: String?
)

data class CreateCollectionRequest(
    val name: String,
    val description: String? = null
)

data class UpdateCollectionRequest(
    val name: String,
    val description: String? = null
)

data class AddToCollectionRequest(
    @com.google.gson.annotations.SerializedName("audiobook_id")
    val audiobookId: Int
)

data class ReorderCollectionRequest(
    val order: List<Int>
)

data class CollectionForBook(
    val id: Int,
    val name: String,
    @com.google.gson.annotations.SerializedName("contains_book")
    val containsBook: Int
)

// Upload Data Classes
data class UploadResponse(
    val success: Boolean,
    val audiobook: com.sappho.audiobooks.domain.model.Audiobook?,
    val message: String?,
    val error: String?
)
