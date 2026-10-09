package com.sappho.audiobooks.presentation.admin

import com.sappho.audiobooks.data.remote.UserInfo
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What the admin user row shows about a user's listening. */
sealed interface ListeningLine {
    /** Single-string form, e.g. "Listened to Golden Son · Today 6:12 PM". */
    val text: String

    /** The server reports listening but this user has none. */
    data object NoneYet : ListeningLine {
        override val text: String get() = UserActivityFormatter.NO_LISTENING
    }

    /** [title] is null when the server didn't send one. [time] is already formatted. */
    data class Listened(val title: String?, val time: String) : ListeningLine {
        val lead: String get() = if (title != null) "Listened to $title" else "Listened"
        override val text: String get() = "$lead · $time"
    }
}

/**
 * Formats the activity fields on GET /api/users (last_listened_at,
 * last_listened_title, last_login_at) for the admin user list.
 *
 * Timestamps arrive as UTC "YYYY-MM-DD HH:MM:SS" (or null) and are shown as
 * absolute local times: "Today 6:12 PM", "Yesterday 9:03 AM", "Tue 8:15 PM"
 * within the past week, then "Oct 3", or "Oct 3, 2025" outside this year.
 */
object UserActivityFormatter {

    const val NO_LISTENING = "No listening yet"
    const val LOGIN_NOT_RECORDED = "Not recorded yet"

    private val sqliteUtc: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Parses a server timestamp as UTC. Accepts the SQLite form and ISO-8601; null on anything else. */
    fun parse(timestamp: String?): Instant? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(timestamp, sqliteUtc).toInstant(ZoneOffset.UTC)
        } catch (e: Exception) {
            try {
                Instant.parse(timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Absolute time relative to [now]'s calendar day, in [now]'s zone. */
    fun formatWhen(instant: Instant, now: ZonedDateTime, locale: Locale = Locale.getDefault()): String {
        val local = instant.atZone(now.zone)
        val daysAgo = ChronoUnit.DAYS.between(local.toLocalDate(), now.toLocalDate())
        val time = DateTimeFormatter.ofPattern("h:mm a", locale).format(local)
        return when {
            daysAgo <= 0 -> "Today $time"
            daysAgo == 1L -> "Yesterday $time"
            daysAgo < 7 -> "${DateTimeFormatter.ofPattern("EEE", locale).format(local)} $time"
            local.year == now.year -> DateTimeFormatter.ofPattern("MMM d", locale).format(local)
            else -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(local)
        }
    }

    /** Null when the server doesn't report listening at all (older server): hide the line. */
    fun listeningLine(
        user: UserInfo,
        now: ZonedDateTime = ZonedDateTime.now(),
        locale: Locale = Locale.getDefault()
    ): ListeningLine? {
        if (!user.reportsListening) return null
        val at = parse(user.lastListenedAt) ?: return ListeningLine.NoneYet
        val title = user.lastListenedTitle?.trim()?.takeIf { it.isNotEmpty() }
        return ListeningLine.Listened(title, formatWhen(at, now, locale))
    }

    /** Detail value for "Last login", or "Not recorded yet". */
    fun lastLoginLabel(
        user: UserInfo,
        now: ZonedDateTime = ZonedDateTime.now(),
        locale: Locale = Locale.getDefault()
    ): String = parse(user.lastLoginAt)?.let { formatWhen(it, now, locale) } ?: LOGIN_NOT_RECORDED
}
