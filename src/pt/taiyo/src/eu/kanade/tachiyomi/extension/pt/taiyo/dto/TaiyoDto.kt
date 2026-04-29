package eu.kanade.tachiyomi.extension.pt.taiyo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchResultDto(
    val results: List<SearchWrapper>,
) {
    val mangas: List<AdditionalInfoDto> get() = results.firstOrNull()?.hits ?: emptyList()
}

@Serializable
class SearchWrapper(
    val hits: List<AdditionalInfoDto>,
)

@Serializable
class AdditionalInfoDto(
    val id: String,
    val synopsis: String? = null,
    val status: String? = null,
    val genres: List<Genre>? = null,
    @SerialName("mainCoverId")
    val coverId: String? = null,
    val titles: List<TitleDto>,
)

enum class Genre(val portugueseName: String) {
    ACTION("Ação"),
    ADVENTURE("Aventura"),
    COMEDY("Comédia"),
    DRAMA("Drama"),
    ECCHI("Ecchi"),
    FANTASY("Fantasia"),
    HENTAI("Hentai"),
    HORROR("Horror"),
    MAHOU_SHOUJO("Mahou Shoujo"),
    MECHA("Mecha"),
    MUSIC("Música"),
    MYSTERY("Mistério"),
    PSYCHOLOGICAL("Psicológico"),
    ROMANCE("Romance"),
    SCI_FI("Sci-fi"),
    SLICE_OF_LIFE("Slice of Life"),
    SPORTS("Esportes"),
    SUPERNATURAL("Sobrenatural"),
    THRILLER("Thriller"),
}

@Serializable
class TitleDto(val title: String, val language: String, val priority: Int)

@Serializable
class ChapterListDto(val chapters: List<ChapterDto>, val totalPages: Int)

@Serializable
class ChapterDto(
    val id: String,
    val number: Float,
    val scans: List<ScanDto>,
    val title: String?,
    val createdAt: String? = null,
)

@Serializable
class ScanDto(val name: String)

@Serializable
class MediaChapterDto(
    val id: String,
    val media: ItemId,
    val pages: List<ItemId>,
)

@Serializable
class ItemId(val id: String)
