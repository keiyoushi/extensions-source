package eu.kanade.tachiyomi.extension.pt.taiyo.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResponseDto<T>(val result: ResultDto<T>) {
    val data: T = result.data.json
}

@Serializable
data class ResultDto<T>(val data: DataDto<T>)

@Serializable
data class DataDto<T>(val json: T)

@Serializable
data class SearchResultDto(
    val id: String,
    val title: String,
    val coverId: String? = null,
)

@Serializable
data class AdditionalInfoDto(
    val synopsis: String? = null,
    val status: String? = null,
    val genres: List<Genre>? = null,
    val titles: List<TitleDto>? = null,
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
data class TitleDto(val title: String, val language: String)

@Serializable
data class ChapterListDto(val chapters: List<ChapterDto>, val totalPages: Int)

@Serializable
data class ChapterDto(
    val id: String,
    val number: Float,
    val scans: List<ScanDto>,
    val title: String?,
    val createdAt: String? = null,
)

@Serializable
data class ScanDto(val name: String)

@Serializable
data class MediaChapterDto(
    val id: String,
    val media: ItemId,
    val pages: List<ItemId>,
)

@Serializable
data class ItemId(val id: String)
