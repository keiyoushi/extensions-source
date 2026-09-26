package eu.kanade.tachiyomi.extension.es.mangacrab

import kotlinx.serialization.Serializable

@Serializable
data class MangaCrabMangasDto(
    val items: List<MangaCrabMangaDto> = emptyList(),
    val pagination: MangaCrabPaginationDto = MangaCrabPaginationDto(),
)

@Serializable
data class MangaCrabPaginationDto(
    val page: Int = 1,
    val per_page: Int = 24,
    val total_items: Int = 0,
    val total_pages: Int = 1,
    val has_next: Boolean = false,
)

@Serializable
data class MangaCrabMangaDto(
    val id: Long,
    val title: String,
    val description: String = "",
    val cover: String? = null,
    val permalink: String,
    val status: MangaCrabLabelDto? = null,
    val genres: List<MangaCrabGenreDto> = emptyList(),
)

@Serializable
data class MangaCrabLabelDto(
    val raw: String = "",
    val label: String = "",
)

@Serializable
data class MangaCrabGenreDto(
    val id: Long,
    val name: String,
    val slug: String,
)

@Serializable
data class MangaCrabChaptersDto(
    val items: List<MangaCrabChapterDto> = emptyList(),
    val pagination: MangaCrabPaginationDto = MangaCrabPaginationDto(),
)

@Serializable
data class MangaCrabChapterDto(
    val index: Int,
    val title: String,
    val label: String = "",
    val number: String = "",
    val date: String = "",
    val chapter_slug: String,
    val chapter_id: Long,
    val is_locked: Boolean = false,
    val is_vip_chapter: Boolean = false,
    val can_download: Boolean = true,
    val link: String,
    val chapter_type: String = "manga",
    val security: MangaCrabSecurityDto? = null,
    val content: MangaCrabChapterContentDto? = null,
)

@Serializable
data class MangaCrabSecurityDto(
    val enabled: Int = 0,
    val mode: String = "",
    val header: String = "",
)

@Serializable
data class MangaCrabChapterContentDto(
    val type: String = "",
    val pages: List<String> = emptyList(),
)
