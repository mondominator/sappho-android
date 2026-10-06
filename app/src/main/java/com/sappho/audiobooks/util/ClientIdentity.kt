package com.sappho.audiobooks.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.sappho.audiobooks.BuildConfig
import okhttp3.Interceptor

/**
 * Identifies this client to the server. The server records `X-Device-Name` on
 * listening sessions (`server/utils/deviceName.js`); without it every session
 * shows as "Unknown".
 */
object ClientIdentity {
    const val HEADER_DEVICE_NAME = "X-Device-Name"
    const val HEADER_APP_VERSION = "X-App-Version"
    const val HEADER_USER_AGENT = "User-Agent"
    const val PLATFORM = "Android"

    private const val MAX_DEVICE_NAME_LENGTH = 100

    /**
     * OkHttp rejects header values with non-ASCII characters (it throws), and a
     * user-chosen device name often has a curly apostrophe ("Mondo’s Pixel").
     * Map common punctuation to ASCII, drop anything else outside printable
     * ASCII, collapse whitespace and cap the length.
     */
    fun sanitizeHeaderValue(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val mapped = raw
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('–', '-')
            .replace('—', '-')
        val ascii = mapped.replace(Regex("\\s+"), " ")
            .filter { it in ' '..'~' }
            .replace(Regex(" {2,}"), " ")
            .trim()
        return ascii.take(MAX_DEVICE_NAME_LENGTH).trim().ifEmpty { null }
    }

    /** Pick the user's device name if set, else "Manufacturer Model". */
    fun resolveDeviceName(userDeviceName: String?, manufacturer: String?, model: String?): String {
        sanitizeHeaderValue(userDeviceName)?.let { return it }
        val mfr = manufacturer.orEmpty().trim()
        val mdl = model.orEmpty().trim()
        val combined = when {
            mdl.isEmpty() -> mfr
            mfr.isEmpty() || mdl.startsWith(mfr, ignoreCase = true) -> mdl
            else -> "${mfr.replaceFirstChar { it.uppercase() }} $mdl"
        }
        return sanitizeHeaderValue(combined) ?: PLATFORM
    }

    fun deviceName(context: Context): String {
        val userName = try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } catch (_: Exception) {
            null
        }
        return resolveDeviceName(userName, Build.MANUFACTURER, Build.MODEL)
    }

    fun appVersion(): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    fun userAgent(): String =
        "Sappho-Android/${BuildConfig.VERSION_NAME} (${sanitizeHeaderValue(Build.MODEL) ?: PLATFORM}; Android ${Build.VERSION.RELEASE})"

    /**
     * Adds device name, app version and a descriptive User-Agent to every
     * request. Values are computed once; the device name rarely changes.
     */
    fun interceptor(context: Context): Interceptor {
        val appContext = context.applicationContext
        val name by lazy { deviceName(appContext) }
        val version by lazy { appVersion() }
        val agent by lazy { sanitizeHeaderValue(userAgent()) ?: "Sappho-Android" }
        return Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(HEADER_DEVICE_NAME, name)
                    .header(HEADER_APP_VERSION, version)
                    .header(HEADER_USER_AGENT, agent)
                    .build()
            )
        }
    }
}
