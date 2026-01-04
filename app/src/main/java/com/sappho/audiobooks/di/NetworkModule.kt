package com.sappho.audiobooks.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sappho.audiobooks.data.remote.SapphoApi
import com.sappho.audiobooks.data.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authRepository: AuthRepository): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Only log in debug builds for performance
            level = if (com.sappho.audiobooks.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val originalHost = original.url.host

                // Get the current server URL dynamically
                val serverUrl = authRepository.getServerUrlSync()
                val serverHost = serverUrl?.toHttpUrlOrNull()?.host

                // Check if this is an external URL (not going to our server)
                // External URLs should be passed through unchanged without auth headers
                val isExternalUrl = serverHost != null &&
                    originalHost != serverHost &&
                    originalHost != "localhost" &&
                    !originalHost.startsWith("192.168.") &&
                    !originalHost.startsWith("10.") &&
                    originalHost != "127.0.0.1"

                if (isExternalUrl) {
                    // External URL - pass through unchanged (no auth, no URL rewriting)
                    chain.proceed(original)
                } else {
                    val request = if (serverUrl != null && serverUrl.isNotBlank()) {
                        // Parse the server URL to extract components
                        val baseUrl = if (!serverUrl.endsWith("/")) "$serverUrl/" else serverUrl
                        val httpUrl = baseUrl.toHttpUrlOrNull()

                        if (httpUrl != null) {
                            // Rebuild the request URL with the dynamic server URL, preserving query parameters
                            val urlBuilder = httpUrl.newBuilder()
                                .addPathSegments(original.url.encodedPath.removePrefix("/"))

                            // Copy all query parameters from the original request
                            for (i in 0 until original.url.querySize) {
                                val name = original.url.queryParameterName(i)
                                val value = original.url.queryParameterValue(i)
                                if (value != null) {
                                    urlBuilder.addQueryParameter(name, value)
                                }
                            }

                            val newUrl = urlBuilder.build()

                            val requestBuilder = original.newBuilder().url(newUrl)

                            // Add authorization header if token exists
                            val token = authRepository.getTokenSync()
                            if (token != null) {
                                requestBuilder.header("Authorization", "Bearer $token")
                            }

                            requestBuilder.build()
                        } else {
                            // Fall back to original if URL parsing fails
                            val requestBuilder = original.newBuilder()
                            val token = authRepository.getTokenSync()
                            if (token != null) {
                                requestBuilder.header("Authorization", "Bearer $token")
                            }
                            requestBuilder.build()
                        }
                    } else {
                        // No server URL set, use original
                        val requestBuilder = original.newBuilder()
                        val token = authRepository.getTokenSync()
                        if (token != null) {
                            requestBuilder.header("Authorization", "Bearer $token")
                        }
                        requestBuilder.build()
                    }

                    chain.proceed(request)
                }
            }
            // Interceptor to detect auth errors (401 Unauthorized or 403 Forbidden with token error)
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401 || response.code == 403) {
                    // Token expired or invalid - trigger auth error
                    authRepository.triggerAuthError()
                }
                response
            }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        @ApplicationContext context: Context,
        authRepository: AuthRepository
    ): Retrofit {
        // Get base URL from auth repository (where server URL is stored)
        val baseUrl = authRepository.getServerUrlSync() ?: "http://192.168.1.100:3002"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideSapphoApi(retrofit: Retrofit): SapphoApi {
        return retrofit.create(SapphoApi::class.java)
    }
}
