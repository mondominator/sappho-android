package com.sappho.audiobooks.di

import com.google.common.truth.Truth.assertThat
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
}
