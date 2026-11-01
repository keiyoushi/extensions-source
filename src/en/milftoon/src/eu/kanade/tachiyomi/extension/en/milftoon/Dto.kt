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
class DetailWrapper(val data: DetailData)

@Serializable
class DetailData(
    val comic: ComicDetail,
    val chapter: ChapterDetail? = null,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class ComicDetail(
    val slug: String,
    val title: String,
    val coverPath: String? = null,
)

@Serializable
class ChapterDetail(
    val slug: String? = null,
    val name: String = "",
    val publishedAt: String? = null,
)

@Serializable
class PageDto(val src: String)
