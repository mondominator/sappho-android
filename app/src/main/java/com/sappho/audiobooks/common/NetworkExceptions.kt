package com.sappho.audiobooks.common

import java.io.IOException

// Custom exceptions for better error handling
class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)
class NetworkTimeoutException(message: String = "Request timed out") : IOException(message)
class NoInternetException(message: String = "No internet connection") : IOException(message)
class ServerException(val code: Int, message: String) : IOException("Server error $code: $message")
class UnauthorizedException(message: String = "Authentication required") : IOException(message)
class NotFoundException(message: String = "Resource not found") : IOException(message)
class EmptyBodyException(message: String = "Empty response body") : IOException(message)