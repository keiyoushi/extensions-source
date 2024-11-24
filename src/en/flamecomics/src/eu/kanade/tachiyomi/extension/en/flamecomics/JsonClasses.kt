package eu.kanade.tachiyomi.extension.en.flamecomics

import kotlinx.serialization.Serializable

@Serializable
class PageData(
    val props: Props,
)

@Serializable
class Props(
    val pageProps: PageProps,
)

@Serializable
class PageProps(
    val chapters: List<Chapter>,
    val series: Series,
)

@Serializable
class SearchPageData(
    val props: SProps,
)

@Serializable
class SProps(
    val pageProps: SPageProps,
)

@Serializable
class SPageProps(
    val series: List<Series>,
)

@Serializable
class Series(
    val title: String,
    val cover: String,
    val author: String,
    val status: String,
    val series_id: Int,
)

@Serializable
class Chapter(
    val chapter: Double,
    val title: String?,
    val release_date: Long,
    val token: String,
)
