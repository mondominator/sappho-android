package com.sappho.audiobooks.domain.model

import com.google.gson.annotations.SerializedName

data class ConversionStatusResponse(
    val active: Boolean,
    val job: ConversionJob?
)

data class ConversionJob(
    @SerializedName("id") val jobId: String,
    val status: String,
    val progress: Int,
    val message: String?,
    @SerializedName("audiobookTitle") val audiobookTitle: String?,
    @SerializedName("audiobookId") val audiobookId: Int?,
    val error: String?
)
