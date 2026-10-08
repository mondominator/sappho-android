package com.sappho.audiobooks.util

/**
 * Error codes the server (0.16.0+) returns when a request for a book mirrored
 * from a linked server fails on the linked side. None of them mean our own
 * session is invalid: they must never log the user out or clear tokens.
 */
object RemoteErrors {
    const val REMOTE_UNAVAILABLE = "REMOTE_UNAVAILABLE"
    const val REMOTE_AUTH_FAILED = "REMOTE_AUTH_FAILED"
    const val REMOTE_ERROR = "REMOTE_ERROR"
    const val REMOTE_INVALID = "REMOTE_INVALID"
    const val REMOTE_BOOK_GONE = "REMOTE_BOOK_GONE"
    const val REMOTE_BOOK_READ_ONLY = "REMOTE_BOOK_READ_ONLY"

    private val CODES = setOf(
        REMOTE_UNAVAILABLE, REMOTE_AUTH_FAILED, REMOTE_ERROR,
        REMOTE_INVALID, REMOTE_BOOK_GONE, REMOTE_BOOK_READ_ONLY
    )

    fun isRemoteError(code: String?): Boolean = code in CODES

    /** A message for the user, or null when [code] is not a linked-server error. */
    fun messageFor(code: String?): String? = when (code) {
        REMOTE_UNAVAILABLE -> "The server this book comes from can't be reached right now. Try again later."
        REMOTE_AUTH_FAILED -> "The server this book comes from refused the connection. An admin needs to check the linked server."
        REMOTE_ERROR, REMOTE_INVALID -> "The server this book comes from had a problem. Try again later."
        REMOTE_BOOK_GONE -> "This book is no longer available from the server it came from."
        REMOTE_BOOK_READ_ONLY -> "Books from a linked server can't be changed here."
        else -> null
    }
}

/**
 * Decides whether an HTTP response means our own session is gone. Only a 401
 * from our own server does: linked-server failures arrive as 502/503/404/409
 * and the server never passes a remote 401 through, but a 401 that names a
 * linked-server code, or one from a host other than our server (a pass-through
 * external URL), is still not a reason to sign the user out.
 */
object AuthErrorPolicy {
    private const val HTTP_UNAUTHORIZED = 401

    fun shouldLogout(
        statusCode: Int,
        requestHost: String,
        serverHost: String?,
        errorCode: String? = null
    ): Boolean {
        if (statusCode != HTTP_UNAUTHORIZED) return false
        if (RemoteErrors.isRemoteError(errorCode)) return false
        return serverHost == null || requestHost.equals(serverHost, ignoreCase = true)
    }
}
