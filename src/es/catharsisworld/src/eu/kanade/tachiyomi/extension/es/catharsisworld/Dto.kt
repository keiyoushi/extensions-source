package eu.kanade.tachiyomi.extension.es.catharsisworld

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class PagesDto(
    val paginas: List<JsonElement>,
    val chapterSessionToken: String,
)

@Serializable
class PageTicketRequest(
    private val pageIndex: Int,
    private val sessionToken: String,
)

@Serializable
class PageTicketResponse(
    val signedToken: String,
    val scrambleKey: String? = null,
    val xorByte: Int,
)

@Serializable
class ChapterTokenPayload(
    val cid: Int,
    val exp: Long,
)
