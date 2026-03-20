package com.sappho.audiobooks.domain.model

import com.google.gson.annotations.SerializedName

data class ListeningSession(
    val id: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("stopped_at") val stoppedAt: String?,
    @SerializedName("start_position") val startPosition: Int,
    @SerializedName("end_position") val endPosition: Int?,
    @SerializedName("device_name") val deviceName: String?
)

data class ListeningSessionsResponse(
    val sessions: List<ListeningSession>
)
