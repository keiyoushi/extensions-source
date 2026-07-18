package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class DetailsResponse(
    private val authors: List<Author>?,
    private val companyName: String?,
    private val copyRightString: String?,
    private val productExplanationShort: String?,
    private val productExplanationDetails: String?,
    private val coverImageUrl: String?,
    private val labelName: String?,
    val seriesId: Int,
    private val seriesName: String,
    private val seriesNameKana: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = seriesId.toString()
        title = seriesName
        author = authors?.joinToString { it.authorName }
        description = buildString {
            productExplanationShort?.let { append("$it\n\n") }
            productExplanationDetails?.let { append(it) }
            seriesNameKana?.let { append("\n\nAlternative Title: $it") }
            companyName?.let { append("\n\nPublisher: $it") }
            labelName?.let { append("\n\nLabel: $it") }
            copyRightString?.let { append("\n\n$it") }
        }
        thumbnail_url = coverImageUrl
    }
}

@Serializable
class Author(
    val authorName: String,
)

@Serializable
class LibraryResponse(
    val holdBookList: HoldBookList,
)

@Serializable
class HoldBookList(
    val entities: List<Entity>,
    val totalPage: Int,
)

@Serializable
class Entity(
    private val seriesId: Int,
    private val seriesName: String,
    private val imageUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = seriesId.toString()
        title = seriesName
        thumbnail_url = imageUrl?.getHiResCoverFromLegacyUrl()
    }
}
