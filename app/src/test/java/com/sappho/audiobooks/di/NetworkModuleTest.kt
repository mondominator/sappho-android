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

    // --- isExternalRequest (changing the server on the login screen) ---

    @Test
    fun `retrofit requests are ours even after the server url changed`() {
        // Given: Retrofit was built at startup; the user then typed a new public server.
        // The old code built Retrofit on the OLD stored host, took its requests for an
        // external site and sent the login to the old server (an SSL error).
        val retrofitHost = NetworkModule.PLACEHOLDER_BASE_URL.toHttpUrl().host

        // Then: the request is rewritten onto the new server, not passed through
        assertThat(NetworkModule.isExternalRequest(retrofitHost, "pyro-sappho.bitstorm.ca")).isFalse()
    }

    @Test
    fun `requests for the current server are ours`() {
        assertThat(NetworkModule.isExternalRequest("pyro-sappho.bitstorm.ca", "pyro-sappho.bitstorm.ca")).isFalse()
    }

    @Test
    fun `requests for another public host are external`() {
        assertThat(NetworkModule.isExternalRequest("images.example.com", "pyro-sappho.bitstorm.ca")).isTrue()
    }

    @Test
    fun `private network hosts are treated as ours`() {
        assertThat(NetworkModule.isExternalRequest("192.168.1.50", "pyro-sappho.bitstorm.ca")).isFalse()
    }

    @Test
    fun `nothing is external before a server url is saved`() {
        assertThat(NetworkModule.isExternalRequest("images.example.com", null)).isFalse()
    }

    @Test
    fun `the placeholder base url is retrofit-safe`() {
        // Retrofit requires a trailing slash; .invalid never resolves (RFC 2606)
        assertThat(NetworkModule.PLACEHOLDER_BASE_URL).endsWith("/")
        assertThat(NetworkModule.PLACEHOLDER_BASE_URL.toHttpUrl().host).endsWith(".invalid")
    }

    // --- rewriteUrlForServer (subpath deployments must not double-append the path) ---

    @Test
    fun `should rewrite retrofit-relative request onto subpath server url`() {
        // Given: Retrofit's base is the path-free placeholder, so its request
        // paths never include the subpath
        val original = "${NetworkModule.PLACEHOLDER_BASE_URL}api/audiobooks/meta/recent".toHttpUrl()

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
