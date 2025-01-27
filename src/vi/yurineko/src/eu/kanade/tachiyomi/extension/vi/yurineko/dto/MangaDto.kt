package eu.kanade.tachiyomi.extension.vi.yurineko.dto

import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    val id: Int,
    val originalName: String,
    val otherName: String,
    val description: String,
    val status: Int,
    val thumbnail: String,
    val team: ArrayList<Team> = arrayListOf(),
    val author: ArrayList<Author> = arrayListOf(),
    val tag: ArrayList<TagDto> = arrayListOf(),
    val chapters: ArrayList<ChapterDto> = arrayListOf(),
)

@Serializable
data class MangaListDto(
    val result: List<MangaDto>,
    val resultCount: Int,
)
