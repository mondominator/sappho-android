package com.sappho.audiobooks.domain.model

/**
 * Represents the state of a file upload operation.
 * Used by MainViewModel.
 */
enum class UploadState {
    IDLE,
    UPLOADING,
    SUCCESS,
    ERROR
}
