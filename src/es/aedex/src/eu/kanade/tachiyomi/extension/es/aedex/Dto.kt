package eu.kanade.tachiyomi.extension.es.aedex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class FreeReaderRequest(
    private val slug: String,
    private val chapter: String,
    private val modo: String,
)

@Serializable
class FreeReaderDto(
    @SerialName("ws_url") val wsUrl: String,
    @SerialName("ws_ticket") val wsTicket: String,
)

@Serializable
class FreeReaderFrameDto(
    val type: String,
    val challenge: String? = null,
    val code: String? = null,
    @SerialName("redirect_url") val redirectUrl: String? = null,
    @SerialName("redirect_data") val redirectData: String? = null,
)

@Serializable
class FreeReaderAckDto(
    private val type: String,
    private val challenge: String,
    private val sig: String,
)

// The reader page's `window.__BV_CTX__`
@Serializable
class LectorDto(
    @SerialName("t") val token: String,
    @SerialName("h") val liveKey: String,
    val now: Long,
)

@Serializable
class LiveRequest(
    @SerialName("h") private val liveKey: String,
)

@Serializable
class LiveTokenDto(
    @SerialName("l") val token: String,
    val ttl: Long,
)
