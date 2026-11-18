package eu.kanade.tachiyomi.extension.es.aedex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
class LectorDto(
    val t: String,
    val h: String,
    val now: Long,
    val xorByte: Int,
)

@Serializable
class LiveTokenRequest(
    private val h: String,
)

@Serializable
class LiveTokenDto(
    @SerialName("l")
    val token: String,
    val ttl: Long? = null,
)
