package com.sappho.audiobooks.domain.model

import com.google.gson.annotations.SerializedName

data class Audiobook(
    val id: Int,
    val title: String,
    val author: String?,
    val narrator: String?,
    val series: String?,
    @SerializedName("series_position") val seriesPosition: Float?,
    val duration: Int?,
    val genre: String?,
    @SerializedName("published_year") val publishYear: Int?,
    val isbn: String?,
    val description: String?,
    @SerializedName("cover_image") val coverImage: String?,
    @SerializedName("file_count") val fileCount: Int,
    @SerializedName("is_multi_file") val isMultiFile: Int? = null,
    @SerializedName("created_at") val createdAt: String,
    val progress: Progress?,
    val chapters: List<Chapter>? = null
)

data class Progress(
    val id: Int? = null,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("audiobook_id") val audiobookId: Int? = null,
    val position: Int,
    val completed: Int,
    @SerializedName("last_listened") val lastListened: String? = null,
    @SerializedName("current_chapter") val currentChapter: Int? = null
)

data class Chapter(
    val id: Int,
    @SerializedName("audiobook_id") val audiobookId: Int,
    @SerializedName("file_id") val fileId: Int,
    @SerializedName("start_time") val startTime: Double,
    @SerializedName("end_time") val endTime: Double?,
    val title: String?,
    val duration: Double?
)

data class AudiobookFile(
    val id: Int,
    val audiobookId: Int,
    val filename: String,
    val filepath: String,
    val size: Long,
    val duration: Int?,
    val mimeType: String?
)

data class AuthResponse(
    val token: String,
    val user: User
)

data class User(
    val id: Int,
    val username: String,
    val email: String?,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("is_admin") val isAdmin: Int,
    val avatar: String?,
    @SerializedName("created_at") val createdAt: String? = null
)

data class UserStats(
    val totalListenTime: Long = 0,
    val booksStarted: Int = 0,
    val booksCompleted: Int = 0,
    val currentlyListening: Int = 0,
    val topAuthors: List<AuthorListenStat> = emptyList(),
    val topGenres: List<GenreListenStat> = emptyList(),
    val recentActivity: List<RecentActivityItem> = emptyList(),
    val activeDaysLast30: Int = 0,
    val currentStreak: Int = 0,
    val avgSessionLength: Float = 0f
)

data class AuthorListenStat(
    val author: String = "",
    val listenTime: Long = 0,
    val bookCount: Int = 0
)

data class GenreListenStat(
    val genre: String = "",
    val listenTime: Long = 0,
    val bookCount: Int = 0
)

data class RecentActivityItem(
    val id: Int,
    val title: String,
    val author: String?,
    @SerializedName("cover_image") val coverImage: String?,
    val position: Int,
    val duration: Int?,
    val completed: Int,
    @SerializedName("updated_at") val updatedAt: String?
)

data class SeriesInfo(
    val series: String,
    @SerializedName("book_count") val bookCount: Int
)

data class AuthorInfo(
    val author: String,
    @SerializedName("book_count") val bookCount: Int
)

data class GenreInfo(
    val genre: String,
    val count: Int,
    @SerializedName("cover_ids") val coverIds: List<Int>
)

// Genre mappings response from server
data class GenreMappingsResponse(
    val genres: Map<String, GenreCategoryData>,
    val defaults: GenreMetadata
)

data class GenreCategoryData(
    val keywords: List<String>,
    val colors: List<String>,
    val icon: String
)

data class GenreMetadata(
    val colors: List<String>,
    val icon: String
)

data class AudiobooksResponse(
    val audiobooks: List<Audiobook>
)
