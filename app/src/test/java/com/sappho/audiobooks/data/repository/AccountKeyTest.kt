package com.sappho.audiobooks.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class AccountKeyTest {

    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("{\"alg\":\"HS256\"}".toByteArray()) + "." +
            enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test
    fun `key combines normalised server and JWT user id`() {
        val token = jwt("""{"id":12,"username":"amy","jti":"x"}""")
        assertThat(AccountKey.of("HTTPS://Sappho.Example.com/", token)).isEqualTo("https://sappho.example.com#12")
    }

    @Test
    fun `different users on one server get different keys`() {
        val a = AccountKey.of("https://s", jwt("""{"id":1}"""))
        val b = AccountKey.of("https://s", jwt("""{"id":2}"""))
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `token refresh for the same user keeps the key`() {
        val a = AccountKey.of("https://s", jwt("""{"id":1,"jti":"a"}"""))
        val b = AccountKey.of("https://s", jwt("""{"id":1,"jti":"b"}"""))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `no token, no server or an api key gives null`() {
        assertThat(AccountKey.of("https://s", null)).isNull()
        assertThat(AccountKey.of(null, jwt("""{"id":1}"""))).isNull()
        assertThat(AccountKey.of("https://s", "sapho_abcdef")).isNull()
    }

    @Test
    fun `directory name is stable and filesystem safe`() {
        val dir = AccountKey.directoryName("https://s#1")
        assertThat(dir).matches("[0-9a-f]{16}")
        assertThat(AccountKey.directoryName("https://s#1")).isEqualTo(dir)
    }
}
