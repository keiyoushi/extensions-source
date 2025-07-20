package eu.kanade.tachiyomi.extension.vi.mimihentai

import kotlinx.serialization.Serializable

@Serializable
class MangaDTO(
    val data: ArrayList<Manga> = arrayListOf(),
    val totalPage: Long,
    val currentPage: Long,
)

@Serializable
class Manga(
    val id: Long,
    val title: String,
    val coverUrl: String,
    val description: String,
    val authors: ArrayList<Author> = arrayListOf(),
    val genres: ArrayList<Genre> = arrayListOf(),
)

@Serializable
class Author(
    val name: String,
)

@Serializable
class Genre(
    val name: String,
)
