package eu.kanade.tachiyomi.extension.fr.yureiscan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResultDto(
    private val id: Int,
    private val title: String,
    @SerialName("cover_image") private val coverImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/manga.php?id=$id"
        title = this@SearchResultDto.title
        thumbnail_url = coverImage
    }
}
