package com.sappho.audiobooks.presentation.admin

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.sappho.audiobooks.data.remote.UserInfo
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class UserActivityFormatterTest {

    // Thursday 2026-10-08 6:30 PM in Denver (UTC-6) = 2026-10-09 00:30 UTC.
    private val zone = ZoneId.of("America/Denver")
    private val now = ZonedDateTime.of(2026, 10, 8, 18, 30, 0, 0, zone)
    private val locale = Locale.US

    private fun whenOf(utc: String) =
        UserActivityFormatter.formatWhen(UserActivityFormatter.parse(utc)!!, now, locale)

    /** A user as decoded from a server that sends the activity fields. */
    private fun user(listenedAt: String?, title: String? = null, login: String? = null) = UserInfo(
        id = 1,
        username = "alice",
        email = null,
        isAdmin = 0,
        createdAt = null,
        lastListenedAtRaw = listenedAt?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE,
        lastListenedTitle = title,
        lastLoginAt = login
    )

    // ---- parsing ----

    @Test
    fun `parses server timestamps as UTC`() {
        assertThat(UserActivityFormatter.parse("2026-10-08 09:30:00"))
            .isEqualTo(Instant.parse("2026-10-08T09:30:00Z"))
    }

    @Test
    fun `null blank and garbage timestamps parse to null`() {
        assertThat(UserActivityFormatter.parse(null)).isNull()
        assertThat(UserActivityFormatter.parse("")).isNull()
        assertThat(UserActivityFormatter.parse("yesterday-ish")).isNull()
    }

    // ---- date styles ----

    @Test
    fun `today shows Today and the local time`() {
        assertThat(whenOf("2026-10-09 00:12:00")).isEqualTo("Today 6:12 PM")
    }

    @Test
    fun `uses the local zone, not the UTC date`() {
        // Oct 8 in UTC, but 9 PM Oct 7 in Denver.
        assertThat(whenOf("2026-10-08 03:00:00")).isEqualTo("Yesterday 9:00 PM")
    }

    @Test
    fun `yesterday shows Yesterday and the time`() {
        assertThat(whenOf("2026-10-07 15:03:00")).isEqualTo("Yesterday 9:03 AM")
    }

    @Test
    fun `within the past week shows the weekday and time`() {
        assertThat(whenOf("2026-10-07 02:15:00")).isEqualTo("Tue 8:15 PM")
        assertThat(whenOf("2026-10-02 18:00:00")).isEqualTo("Fri 12:00 PM")
    }

    @Test
    fun `a week or more ago this year shows month and day`() {
        assertThat(whenOf("2026-10-01 18:00:00")).isEqualTo("Oct 1")
        assertThat(whenOf("2026-09-20 18:00:00")).isEqualTo("Sep 20")
    }

    @Test
    fun `a previous year adds the year`() {
        assertThat(whenOf("2025-12-31 18:00:00")).isEqualTo("Dec 31, 2025")
    }

    @Test
    fun `a slightly future timestamp reads as today`() {
        assertThat(whenOf("2026-10-09 00:31:00")).isEqualTo("Today 6:31 PM")
    }

    // ---- listening line ----

    @Test
    fun `listened line has title and when`() {
        val line = UserActivityFormatter.listeningLine(user("2026-10-09 00:12:00", "Golden Son"), now, locale)
        assertThat(line).isEqualTo(ListeningLine.Listened("Golden Son", "Today 6:12 PM"))
        assertThat(line!!.text).isEqualTo("Listened to Golden Son · Today 6:12 PM")
    }

    @Test
    fun `listened line without a title still shows when`() {
        val line = UserActivityFormatter.listeningLine(user("2026-10-01 18:00:00", title = "  "), now, locale)
        assertThat(line!!.text).isEqualTo("Listened · Oct 1")
    }

    @Test
    fun `no listening yet when last listened is null`() {
        val line = UserActivityFormatter.listeningLine(user(null, title = null), now, locale)
        assertThat(line).isEqualTo(ListeningLine.NoneYet)
        assertThat(line!!.text).isEqualTo("No listening yet")
    }

    @Test
    fun `no listening yet when last listened is unparseable`() {
        assertThat(UserActivityFormatter.listeningLine(user("garbage", "Golden Son"), now, locale))
            .isEqualTo(ListeningLine.NoneYet)
    }

    @Test
    fun `line is hidden when the server omits the fields`() {
        val u = UserInfo(id = 1, username = "alice", email = null, isAdmin = 0, createdAt = null)
        assertThat(UserActivityFormatter.listeningLine(u, now, locale)).isNull()
    }

    // ---- last login ----

    @Test
    fun `last login uses the same date style`() {
        assertThat(UserActivityFormatter.lastLoginLabel(user(null, login = "2026-10-07 15:03:00"), now, locale))
            .isEqualTo("Yesterday 9:03 AM")
    }

    @Test
    fun `last login says not recorded when null`() {
        assertThat(UserActivityFormatter.lastLoginLabel(user(null), now, locale)).isEqualTo("Not recorded yet")
    }

    // ---- JSON decoding (absent vs null) ----

    @Test
    fun `older server response without the fields hides the line`() {
        val json = """{"id":2,"username":"bob","email":null,"is_admin":0,"account_disabled":false,"created_at":"2024-01-01 00:00:00"}"""
        val u = Gson().fromJson(json, UserInfo::class.java)
        assertThat(u.reportsListening).isFalse()
        assertThat(UserActivityFormatter.listeningLine(u, now, locale)).isNull()
        assertThat(UserActivityFormatter.lastLoginLabel(u, now, locale)).isEqualTo("Not recorded yet")
    }

    @Test
    fun `explicit null from a new server shows no listening yet`() {
        val json = """{"id":2,"username":"bob","email":null,"is_admin":0,"created_at":null,
            "last_listened_at":null,"last_listened_title":null,"last_active_at":"2026-10-08 10:00:00","last_login_at":null}"""
        val u = Gson().fromJson(json, UserInfo::class.java)
        assertThat(u.reportsListening).isTrue()
        assertThat(UserActivityFormatter.listeningLine(u, now, locale)).isEqualTo(ListeningLine.NoneYet)
    }

    @Test
    fun `new server response decodes title and time`() {
        val json = """{"id":2,"username":"bob","email":null,"is_admin":1,"created_at":null,
            "last_listened_at":"2026-10-09 00:12:00","last_listened_title":"Golden Son","last_login_at":"2026-10-05 12:00:00"}"""
        val u = Gson().fromJson(json, UserInfo::class.java)
        assertThat(UserActivityFormatter.listeningLine(u, now, locale)!!.text)
            .isEqualTo("Listened to Golden Son · Today 6:12 PM")
        assertThat(UserActivityFormatter.lastLoginLabel(u, now, locale)).isEqualTo("Mon 6:00 AM")
    }
}
