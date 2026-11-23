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

// The reader page's `window.readerContext`
@Serializable
class LectorDto(
    @SerialName("t") val token: String,
    @SerialName("h") val liveKey: String,
    val now: Long,
    val pages: List<String> = emptyList(),
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

// The backend's /system, for the Turnstile sitekey the login page uses
@Serializable
class AedexSystemDto(
    @SerialName("turnstile_sitekey") val turnstileSitekey: String? = null,
)

@Serializable
class AedexCsrfDto(
    @SerialName("csrfToken") val csrfToken: String,
)

// The NextAuth session, which carries the backend access token used as a Bearer
@Serializable
class AedexSessionDto(
    @SerialName("access_token") val accessToken: String? = null,
    private val user: AedexSessionUserDto? = null,
) {
    fun token(): String? = accessToken ?: user?.accessToken
}

@Serializable
class AedexSessionUserDto(
    @SerialName("access_token") val accessToken: String? = null,
)
