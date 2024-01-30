package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaDetailsDto(
    val detail: MangaDto,
    val tags: List<TagDto>,
    val authors: List<AuthorDto>,
    val chapters: List<ChapterDto>,
)
