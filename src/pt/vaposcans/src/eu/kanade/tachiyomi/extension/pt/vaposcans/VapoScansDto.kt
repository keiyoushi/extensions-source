package eu.kanade.tachiyomi.extension.pt.vaposcans

import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    val code: String,
    val cover: String,
    val title: String,
)

@Serializable
class LatestMangaDto(
    val serie_code: String,
    val serie_cover: String,
    val serie_title: String,
) {
    val mangaDto get() = MangaDto(serie_code, serie_cover, serie_title)
}

@Serializable
class MangaCode(val code: String)

@Serializable
class MangaDetailsDto(
    val artist: String,
    val author: String,
    val code: String,
    val cover: String,
    val genres: List<String>,
    val status: String,
    val synopsis: String,
    val title: String,
)

@Serializable
class ChapterDto(
    val number: String,
    val code: String,
    val upload_date: String,
)

@Serializable
class PagesDto(
    val chapter_code: String,
    val images: List<String>,
)
