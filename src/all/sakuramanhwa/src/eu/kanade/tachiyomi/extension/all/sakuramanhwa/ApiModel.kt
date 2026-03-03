package eu.kanade.tachiyomi.extension.all.sakuramanhwa

import kotlinx.serialization.Serializable

@Serializable
internal class ApiMangaInfo(
    val manga: MangaDetails,
    val metaData: MetaDataInfo,
    val chapters: List<ChapterInfo>,
)

@Serializable
internal class MetaDataInfo(
    val follows: Int,
    val views: Int,
)

@Serializable
internal class MangaDetails(
    val id: String,
    val title: String,
    val img: String,
    val description: String?,
    val language: String,
    val slug: String,
    val type: String,
    val status: String?,
    val authors: List<String>?,
    val rating: Float?,
    val create_at: String?,
)

@Serializable
internal class ChapterInfo(
    val id: String,
    val title: String?,
    val create_at: String,
    val number: Float,
)

@Serializable
internal class ApiChapterInfo(
    val chapter: PageInfo,
    val prev_chapter: String?,
    val next_chapter: String?,
)

@Serializable
internal class PageInfo(
    val id: String,
    val number: Float,
    val title: String?,
    val images: List<List<String>>,
)

@Serializable
internal class ApiMangaList(
    val mangas: List<MangaDetails>,
    val next_page: Int?,
    val prev_page: Int?,
    val max_pages: Int?,
)

@Serializable
internal class I18nDictionary(
    val home: I18nHomeDictionary,
    val library: I18nLibraryDictionary,
)

@Serializable
internal class I18nHomeDictionary(
    val updates: I18nHomeUpdatesDictionary,
    val lastUpdatesNormal: String,
)

@Serializable
internal class I18nHomeUpdatesDictionary(
    val buttons: I18nHomeButtonsDictionary,
)

@Serializable
internal class I18nHomeButtonsDictionary(
    val language: Map<String, String>, // all, spanish, english, chinese, raw
    val genres: Map<String, String>, // all, mature, normal
)

@Serializable
internal class I18nLibraryDictionary(
    val title: String,
    val search: String,
    val sort: Map<String, String>, // title, type, rating, date
    val filter: Map<String, String>, // title, category, language, sortBy
)
