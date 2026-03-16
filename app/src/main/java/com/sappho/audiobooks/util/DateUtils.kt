package com.sappho.audiobooks.util

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Formats an ISO 8601 date string into a human-readable relative time string.
 * Examples: "just now", "5 minutes ago", "2 hours ago", "3 days ago", "2 months ago", "1 year ago"
 */
fun formatRelativeTime(isoDateString: String?): String {
    if (isoDateString.isNullOrBlank()) return ""

    return try {
        val instant = try {
            Instant.parse(isoDateString)
        } catch (e: Exception) {
            ZonedDateTime.parse(isoDateString, DateTimeFormatter.ISO_DATE_TIME).toInstant()
        }

        val now = Instant.now()
        val minutes = ChronoUnit.MINUTES.between(instant, now)
        val hours = ChronoUnit.HOURS.between(instant, now)
        val days = ChronoUnit.DAYS.between(instant, now)

        when {
            minutes < 1 -> "just now"
            minutes < 60 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days < 30 -> if (days == 1L) "1 day ago" else "$days days ago"
            days < 365 -> {
                val months = days / 30
                if (months == 1L) "1 month ago" else "$months months ago"
            }
            else -> {
                val years = days / 365
                if (years == 1L) "1 year ago" else "$years years ago"
            }
        }
    } catch (e: Exception) {
        ""
    }
}
