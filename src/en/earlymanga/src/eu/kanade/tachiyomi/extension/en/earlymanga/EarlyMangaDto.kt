package eu.kanade.tachiyomi.extension.en.earlymanga

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val data: List<SearchData>,
    val meta: SearchMeta,
)

@Serializable
data class SearchData(
    val id: Int,
    val title: String,
    val slug: String,
    val cover: String? = null,
)

@Serializable
data class MangaResponse(
    val main_manga: MangaData,
)

@Serializable
data class MangaData(
    val id: Int,
    val title: String,
    val slug: String,
    val alt_titles: List<NameVal>?,
    val authors: List<String>?,
    val artists: List<String>?,
    val all_genres: List<NameVal>?,
    val pubstatus: List<NameVal>,
    val desc: String? = "Unknown",
    val cover: String? = null,
)

@Serializable
data class NameVal(
    val name: String,
)

@Serializable
data class ChapterList(
    val id: Int,
    val slug: String,
    val title: String?,
    val created_at: String?,
    val chapter_number: String,
)

@Serializable
data class PageListResponse(
    val chapter: Chapter,
)

@Serializable
data class Chapter(
    val id: Int,
    val manga_id: Int,
    val slug: String,
    val on_disk: Int,
    val images: List<String>,
)

@Serializable
data class SearchMeta(
    val current_page: Int,
    val last_page: Int,
)

@Serializable
data class FilterResponse(
    val genres: List<Genre>,
    val sub_genres: List<Genre>,
    val contents: List<Genre>,
    val demographics: List<Genre>,
    val formats: List<Genre>,
    val themes: List<Genre>,
)

@Serializable
data class SearchPayload(
    val excludedGenres_all: List<String>,
    val includedGenres_all: List<String>,
    val includedLanguages: List<String>,
    val includedPubstatus: List<String>,
    val list_order: String,
    val list_type: String,
    val term: String,
)

@Serializable
data class Genre(
    val name: String,
)
