package eu.kanade.tachiyomi.extension.id.ryukomik

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ListResponseDto(
    val data: List<ItemDto> = emptyList(),
    val meta: MetaDto? = null,
)

@Serializable
class ItemDto(
    val title: String? = null,
    val slug: String? = null,
    @SerialName("detail_link")
    val detailLink: String? = null,
    val link: String? = null,
    val image: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val source: String? = null,
    val type: String? = null,
    val status: String? = null,
) {
    fun toSManga(defaultSource: String): SManga? {
        val s = slug?.takeIf { it.isNotBlank() }
            ?: detailLink?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: link?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return null
        val mangaSource = source?.takeIf { it.isNotBlank() } ?: defaultSource
        val mangaTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val thumb = image ?: coverUrl

        return SManga.create().apply {
            this.title = mangaTitle
            thumbnail_url = thumb
            url = "/komik/$mangaSource/$s"
        }
    }
}

@Serializable
class MetaDto(
    val currentPage: Int = 1,
    val totalPages: Int = 1,
)
