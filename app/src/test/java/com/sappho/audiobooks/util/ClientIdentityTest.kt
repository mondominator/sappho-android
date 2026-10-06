package com.sappho.audiobooks.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClientIdentityTest {

    @Test
    fun `user device name wins and curly quotes become ASCII`() {
        // OkHttp throws on non-ASCII header values; this name would crash every request
        assertThat(ClientIdentity.resolveDeviceName("Mondo’s Pixel", "Google", "Pixel 8"))
            .isEqualTo("Mondo's Pixel")
    }

    @Test
    fun `falls back to manufacturer and model without duplication`() {
        assertThat(ClientIdentity.resolveDeviceName(null, "samsung", "SM-S918B")).isEqualTo("Samsung SM-S918B")
        assertThat(ClientIdentity.resolveDeviceName("  ", "Google", "Google Pixel 8")).isEqualTo("Google Pixel 8")
    }

    @Test
    fun `nothing known falls back to the platform`() {
        assertThat(ClientIdentity.resolveDeviceName(null, null, null)).isEqualTo("Android")
    }

    @Test
    fun `non-ASCII is stripped, whitespace collapsed, length capped`() {
        assertThat(ClientIdentity.sanitizeHeaderValue("Téléphone   de\tMarie")).isEqualTo("Tlphone de Marie")
        assertThat(ClientIdentity.sanitizeHeaderValue("x".repeat(300))!!.length).isEqualTo(100)
        assertThat(ClientIdentity.sanitizeHeaderValue("电话")).isNull()
    }
}
