package com.sappho.audiobooks.data.remote

import android.util.Log
import com.sappho.audiobooks.data.repository.AuthRepository
import java.io.IOException
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp [Authenticator] that transparently refreshes an expired access token
 * when a request comes back with a 401.
 *
 * OkHttp invokes [authenticate] below the application interceptors (inside the
 * RetryAndFollowUpInterceptor); whatever [Request] we return is retried. When
 * refresh succeeds the application-level 401-trigger interceptor never sees the
 * intermediate 401, so the user is NOT logged out. When we return null, the
 * final 401 propagates up and the 401-trigger interceptor drives logout.
 *
 * @param refreshApi a SEPARATE SapphoApi built on a no-auth client so the
 *   refresh call itself is never intercepted/re-authenticated (avoids loops).
 */
class TokenAuthenticator(
    private val authRepository: AuthRepository,
    private val refreshApi: SapphoApi
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Bail out if we've already retried once — avoids infinite refresh loops.
        if (responseCount(response) >= 2) {
            Log.d(TAG, "Already retried once after refresh; giving up")
            return null
        }

        // The access token that was actually used on the failed request.
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        // Another thread may have refreshed while we were blocked on the lock.
        val current = authRepository.getTokenSync()
        if (current != null && current != failedToken) {
            Log.d(TAG, "Token already refreshed by another request; retrying with current token")
            return response.request.newBuilder()
                .header("Authorization", "Bearer $current")
                .build()
        }

        val refreshToken = authRepository.getRefreshTokenSync()
        if (refreshToken == null) {
            Log.d(TAG, "No refresh token available; cannot refresh")
            return null
        }

        return try {
            val refreshResponse = refreshApi.refreshTokenCall(RefreshTokenRequest(refreshToken)).execute()
            val body = refreshResponse.body()
            val newToken = body?.token
            if (refreshResponse.isSuccessful && newToken != null) {
                Log.d(TAG, "Token refresh succeeded; rotating tokens and retrying request")
                authRepository.saveTokens(newToken, body.refreshToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            } else if (refreshResponse.code() == 401 || refreshResponse.code() == 403) {
                // Definitive rejection: the refresh token itself is invalid/revoked.
                // Clearing tokens (and letting the final 401 drive logout) is correct.
                Log.d(TAG, "Refresh token rejected (code=${refreshResponse.code()}); clearing tokens")
                authRepository.clearToken()
                null
            } else {
                // Transient server failure (5xx, restart, proxy error). Do NOT destroy
                // valid credentials — fail this call as a network error so the final
                // 401 never reaches the logout-trigger interceptor, and a later
                // request can retry the refresh.
                Log.w(TAG, "Token refresh failed transiently (code=${refreshResponse.code()}); keeping tokens")
                throw IOException("Token refresh failed transiently (HTTP ${refreshResponse.code()})")
            }
        } catch (e: IOException) {
            // Network blip / timeout / server briefly down. Keep tokens and propagate
            // as a call failure — the request errors out, but credentials survive and
            // the next request retries the refresh. (Returning null here would surface
            // the original 401 and log the user out via the auth-error interceptor.)
            Log.w(TAG, "Token refresh network failure; keeping tokens for retry", e)
            throw e
        }
    }

    private fun responseCount(response: Response): Int {
        var r: Response? = response.priorResponse
        var count = 1
        while (r != null) {
            count++
            r = r.priorResponse
        }
        return count
    }

    companion object {
        private const val TAG = "TokenAuthenticator"
    }
}
