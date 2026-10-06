package com.sappho.audiobooks.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.sappho.audiobooks.data.repository.AccountKey
import com.sappho.audiobooks.data.repository.AuthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves book covers as `content://<app>.covers/cover/<bookId>` for the media
 * notification, lock screen and Android Auto.
 *
 * Those consumers run in other processes and used to get
 * `https://server/api/audiobooks/<id>/cover?token=<7-day JWT>` — handing the
 * user's full API token to system UI and the car host. Here the cover is
 * fetched in-process with the normal Authorization header and cached; other
 * processes only ever see the content URI.
 *
 * The provider is exported (Android Auto's host must be able to open it) but
 * read-only and limited to cover images by numeric id.
 */
class CoverArtProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoverArtEntryPoint {
        fun okHttpClient(): OkHttpClient
        fun authRepository(): AuthRepository
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/*"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read-only")
        val bookId = parseBookId(uri) ?: throw FileNotFoundException("Not a cover URI")
        val context = context ?: throw FileNotFoundException("No context")
        val entryPoint = EntryPointAccessors.fromApplication(context, CoverArtEntryPoint::class.java)
        val auth = entryPoint.authRepository()
        val serverUrl = auth.getServerUrlSync() ?: throw FileNotFoundException("Not signed in")
        val account = auth.getAccountKeySync() ?: throw FileNotFoundException("Not signed in")

        val cacheDir = File(context.cacheDir, "cover_art/${AccountKey.directoryName(account)}").apply { mkdirs() }
        val cached = File(cacheDir, "$bookId.img")
        if (!cached.exists() || cached.length() == 0L) {
            fetchCover(entryPoint.okHttpClient(), "$serverUrl/api/audiobooks/$bookId/cover", cached)
        }
        return ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun fetchCover(client: OkHttpClient, url: String, dest: File) {
        // openFile runs on a binder thread, so a blocking fetch is fine here.
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    throw FileNotFoundException("Cover fetch failed: HTTP ${response.code}")
                }
                tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            }
            if (!tmp.renameTo(dest)) throw FileNotFoundException("Could not cache cover")
        } catch (e: FileNotFoundException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            Log.w(TAG, "Cover fetch failed", e)
            throw FileNotFoundException(e.message ?: "Cover fetch failed")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "CoverArtProvider"
        private const val PATH = "cover"

        fun authority(context: Context): String = "${context.packageName}.covers"

        fun uriFor(context: Context, audiobookId: Int): Uri =
            Uri.Builder()
                .scheme("content")
                .authority(authority(context))
                .appendPath(PATH)
                .appendPath(audiobookId.toString())
                .build()

        internal fun parseBookId(uri: Uri): Int? {
            val segments = uri.pathSegments
            if (segments.size != 2 || segments[0] != PATH) return null
            return segments[1].toIntOrNull()?.takeIf { it > 0 }
        }

        /** Drop cached covers (logout). */
        fun clearCache(context: Context) {
            File(context.cacheDir, "cover_art").deleteRecursively()
        }
    }
}
