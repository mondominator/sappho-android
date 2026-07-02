package com.sappho.audiobooks.di

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class NetworkModuleTest {

    // --- isPrivateNetworkHost (M6: RFC-1918 coverage) ---

    @Test
    fun `should treat localhost and loopback as private`() {
        // Given / When / Then
        assertThat(NetworkModule.isPrivateNetworkHost("localhost")).isTrue()
        assertThat(NetworkModule.isPrivateNetworkHost("127.0.0.1")).isTrue()
    }

    @Test
    fun `should treat 192_168 slash 16 hosts as private`() {
        assertThat(NetworkModule.isPrivateNetworkHost("192.168.1.100")).isTrue()
        assertThat(NetworkModule.isPrivateNetworkHost("192.168.86.151")).isTrue()
    }

    @Test
    fun `should treat 10 slash 8 hosts as private`() {
        assertThat(NetworkModule.isPrivateNetworkHost("10.0.0.1")).isTrue()
        assertThat(NetworkModule.isPrivateNetworkHost("10.255.255.254")).isTrue()
    }

    @Test
    fun `should treat 172_16 slash 12 hosts as private`() {
        // Given: the full 172.16.0.0/12 block spans second octets 16-31
        assertThat(NetworkModule.isPrivateNetworkHost("172.16.0.1")).isTrue()
        assertThat(NetworkModule.isPrivateNetworkHost("172.20.10.5")).isTrue()
        assertThat(NetworkModule.isPrivateNetworkHost("172.31.255.254")).isTrue()
    }

    @Test
    fun `should not treat 172 hosts outside the slash 12 block as private`() {
        assertThat(NetworkModule.isPrivateNetworkHost("172.15.0.1")).isFalse()
        assertThat(NetworkModule.isPrivateNetworkHost("172.32.0.1")).isFalse()
    }

    @Test
    fun `should not treat public hosts as private`() {
        assertThat(NetworkModule.isPrivateNetworkHost("sappho.bitstorm.ca")).isFalse()
        assertThat(NetworkModule.isPrivateNetworkHost("8.8.8.8")).isFalse()
        assertThat(NetworkModule.isPrivateNetworkHost("193.168.1.1")).isFalse()
    }

    @Test
    fun `should not crash on malformed 172 host`() {
        assertThat(NetworkModule.isPrivateNetworkHost("172.")).isFalse()
        assertThat(NetworkModule.isPrivateNetworkHost("172.abc.0.1")).isFalse()
    }

    // --- sanitizeBaseUrl (M7: Retrofit baseUrl must be path-free with trailing slash) ---

    @Test
    fun `should return default base url when stored url is null`() {
        // Given / When
        val result = NetworkModule.sanitizeBaseUrl(null)

        // Then
        assertThat(result).isEqualTo("http://192.168.1.100:3002/")
    }

    @Test
    fun `should return default base url when stored url is unparseable`() {
        val result = NetworkModule.sanitizeBaseUrl("not a url")

        assertThat(result).isEqualTo("http://192.168.1.100:3002/")
    }

    @Test
    fun `should strip path from stored url`() {
        // Given: AuthRepository trims trailing slashes, so a sub-path install
        // is stored as "https://host/sappho" — which crashes Retrofit's baseUrl
        val result = NetworkModule.sanitizeBaseUrl("https://example.com/sappho")

        // Then: scheme+host only, with the trailing slash Retrofit requires
        assertThat(result).isEqualTo("https://example.com/")
    }

    @Test
    fun `should preserve custom port`() {
        val result = NetworkModule.sanitizeBaseUrl("http://192.168.1.50:3002")

        assertThat(result).isEqualTo("http://192.168.1.50:3002/")
    }

    @Test
    fun `should omit default port for scheme`() {
        val result = NetworkModule.sanitizeBaseUrl("https://sappho.bitstorm.ca")

        assertThat(result).isEqualTo("https://sappho.bitstorm.ca/")
    }

    @Test
    fun `should produce retrofit-safe url for deep path with query`() {
        val result = NetworkModule.sanitizeBaseUrl("https://example.com:8443/a/b/c?x=1")

        assertThat(result).isEqualTo("https://example.com:8443/")
    }

    // --- rewriteUrlForServer (subpath deployments must not double-append the path) ---

    @Test
    fun `should rewrite retrofit-relative request onto subpath server url`() {
        // Given: Retrofit's baseUrl is sanitized to scheme+host, so its request
        // paths never include the subpath
        val original = "https://example.com/api/audiobooks/meta/recent".toHttpUrl()

        // When
        val result = NetworkModule.rewriteUrlForServer(original, "https://example.com/sappho")

        // Then
        assertThat(result.toString())
            .isEqualTo("https://example.com/sappho/api/audiobooks/meta/recent")
    }

    @Test
    fun `should not double-append subpath for absolute url already containing it`() {
        // Given: cover URLs are built as "$serverUrl/api/audiobooks/{id}/cover",
        // so with a subpath deployment the path already starts with the subpath
        val original = "https://example.com/sappho/api/audiobooks/5/cover".toHttpUrl()

        // When
        val result = NetworkModule.rewriteUrlForServer(original, "https://example.com/sappho")

        // Then: previously produced ".../sappho/sappho/api/..."
        assertThat(result.toString())
            .isEqualTo("https://example.com/sappho/api/audiobooks/5/cover")
    }

    @Test
    fun `should not strip path segment that merely shares the subpath as a text prefix`() {
        // Given: "/sapphoapi" is not the "/sappho" subpath even though it starts
        // with the same characters
        val original = "https://example.com/sapphoapi/covers/5".toHttpUrl()

        // When
        val result = NetworkModule.rewriteUrlForServer(original, "https://example.com/sappho")

        // Then
        assertThat(result.toString())
            .isEqualTo("https://example.com/sappho/sapphoapi/covers/5")
    }

    @Test
    fun `should rewrite host and preserve query parameters`() {
        // Given: a request built against an old host with a query string
        val original = "http://192.168.1.100:3002/api/audiobooks/5/stream?token=abc".toHttpUrl()

        // When
        val result = NetworkModule.rewriteUrlForServer(original, "https://example.com")

        // Then
        assertThat(result.toString())
            .isEqualTo("https://example.com/api/audiobooks/5/stream?token=abc")
    }

    @Test
    fun `should append full path when hosts differ even with matching prefix`() {
        // Given: prefix-stripping only applies to requests targeting the server
        // host — other hosts keep their full path
        val original = "http://192.168.1.100:3002/sappho/api/audiobooks/5/cover".toHttpUrl()

        // When
        val result = NetworkModule.rewriteUrlForServer(original, "https://example.com/sappho")

        // Then
        assertThat(result.toString())
            .isEqualTo("https://example.com/sappho/sappho/api/audiobooks/5/cover")
    }

    @Test
    fun `should return null for unparseable server url`() {
        val original = "https://example.com/api/audiobooks/5/cover".toHttpUrl()

        val result = NetworkModule.rewriteUrlForServer(original, "not a url")

        assertThat(result).isNull()
    }

    @Test
    fun `should rewrite unchanged for server url without subpath`() {
        val original = "https://example.com/api/audiobooks/5/cover".toHttpUrl()

        val result = NetworkModule.rewriteUrlForServer(original, "https://example.com")

        assertThat(result.toString()).isEqualTo("https://example.com/api/audiobooks/5/cover")
    }
}
