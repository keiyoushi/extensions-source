package eu.kanade.tachiyomi.extension.es.catharsisworld

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The chapter loader's encrypted field
@Serializable
class ReaderDataDto(
    @SerialName("p") val pageTokens: List<String>,
    @SerialName("r") val heartbeat: String,
)

@Serializable
class HeartbeatRequest(
    @SerialName("h") private val heartbeat: String,
)

@Serializable
class HeartbeatDto(
    @SerialName("l") val liveToken: String,
)
