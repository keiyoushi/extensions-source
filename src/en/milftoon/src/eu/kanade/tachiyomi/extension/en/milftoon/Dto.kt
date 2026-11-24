package eu.kanade.tachiyomi.extension.en.milftoon

import kotlinx.serialization.Serializable

@Serializable
class RouteWrapper(val data: ListData)

@Serializable
class ListData(
    val items: List<ComicItem> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 24,
    val total: Int = 0,
)

@Serializable
class ComicItem(
    val slug: String,
    val title: String,
    val coverPath: String? = null,
    val author: String? = null,
)

@Serializable
class DetailWrapper(
    val data: DetailData,
    val auxiliary: ComicDetail,
)

@Serializable
class PageWrapper(val data: DetailData)

@Serializable
class DetailData(
    val chapter: ChapterDetail? = null,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class ComicDetail(
    val title: String,
    val coverPath: String? = null,
    val synopsisHtml: String? = null,
    val status: String? = null,
    val author: String? = null,
    val genres: List<Term>,
    val tags: List<Term>,
    val chapters: List<ChapterItem>,
)

@Serializable
class Term(val name: String)

@Serializable
class ChapterDetail(val slug: String)

@Serializable
class ChapterItem(
    val slug: String,
    val name: String,
    val nameExtend: String? = null,
    val publishedAt: String? = null,
)

@Serializable
class PageDto(val src: String)
