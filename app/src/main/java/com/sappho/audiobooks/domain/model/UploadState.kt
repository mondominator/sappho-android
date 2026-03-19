package com.sappho.audiobooks.domain.model

/**
 * Represents the state of a file upload operation.
 * Shared across MainViewModel and AdminViewModel.
 */
enum class UploadState {
    IDLE,
    UPLOADING,
    SUCCESS,
    ERROR
}
