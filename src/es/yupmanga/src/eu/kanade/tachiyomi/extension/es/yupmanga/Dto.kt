package eu.kanade.tachiyomi.extension.es.yupmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    val html: String = "",
    private val currentPage: Int = 1,
    private val totalPages: Int = 1,
) {
    fun hasNextPage() = currentPage < totalPages
}

@Serializable
class TokenDto(
    val success: Boolean = false,
    val token: String?,
    @SerialName("chapter_id") val chapterId: String?,
    val error: String?,
    val message: String?,
)

@Serializable
class ChallengeDto(
    val success: Boolean = false,
    @SerialName("challenge_id") val challengeId: String?,
    @SerialName("challenge_js") val challengeJs: String?,
)

@Serializable
class AdTicketDto(
    val chapter: String,
    val nonce: String,
)
