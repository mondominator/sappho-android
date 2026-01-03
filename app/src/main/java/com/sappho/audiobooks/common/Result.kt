package com.sappho.audiobooks.common

/**
 * A generic class that represents a result of an operation
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(
        val exception: Exception,
        val message: String = exception.message ?: "Unknown error"
    ) : Result<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun exceptionOrNull(): Exception? = when (this) {
        is Success -> null
        is Error -> exception
    }
}

// Extension functions for Result
inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

inline fun <T> Result<T>.onError(action: (exception: Exception) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(exception)
    }
    return this
}

inline fun <T, R> Result<T>.map(transform: (value: T) -> R): Result<R> {
    return when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
    }
}

inline fun <T, R> Result<T>.flatMap(transform: (value: T) -> Result<R>): Result<R> {
    return when (this) {
        is Result.Success -> transform(data)
        is Result.Error -> this
    }
}

// Helper function to create Results
inline fun <T> runCatching(block: () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(e)
    }
}