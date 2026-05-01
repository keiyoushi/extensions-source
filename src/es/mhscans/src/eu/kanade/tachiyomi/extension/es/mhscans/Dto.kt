package eu.kanade.tachiyomi.extension.es.mhscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RkTokenResponse(val data: RkTokenData)

@Serializable
class RkTokenData(
    val token: String,
    @SerialName("reader_url") val readerUrl: String,
    @SerialName("chapter_id") val chapterId: Int,
    @SerialName("manga_id") val mangaId: Int,
)
