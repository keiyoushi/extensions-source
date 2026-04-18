package eu.kanade.tachiyomi.extension.es.yupmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    val html: String = "",
    @SerialName("currentPage") private val currentPage: Int = 1,
    @SerialName("totalPages") private val totalPages: Int = 1,
) {
    fun hasNextPage() = currentPage < totalPages
}

@Serializable
class TokenDto(
    val success: Boolean = false,
    val token: String? = null,
    @SerialName("chapter_id") val chapterId: String? = null,
)

@Serializable
class ChallengeDto(
    val success: Boolean = false,
    @SerialName("challenge_id") val challengeId: String? = null,
    @SerialName("challenge_js") val challengeJs: String? = null,
)
