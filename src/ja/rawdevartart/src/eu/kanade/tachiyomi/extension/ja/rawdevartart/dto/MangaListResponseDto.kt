package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ButtonData(
    val prev: Int,
    val next: Int,
)

@Serializable
class PaginationData(
    val button: ButtonData? = null,
)

@Serializable
class MangaListItem(
    @SerialName("manga_id") private val id: Int,
    @SerialName("manga_name") private val name: String,
    @SerialName("manga_cover_img") private val coverImage: String,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = coverImage
    }
}

@Serializable
class MangaListResponseDto(
    @SerialName("manga_list") private val mangaList: List<MangaListItem>,
    private val pagi: PaginationData,
) {
    fun toMangasPage(): MangasPage {
        val manga = mangaList.map { it.toSManga() }
        val hasNextPage = (pagi.button?.next ?: 0) != 0

        return MangasPage(manga, hasNextPage)
    }
}
