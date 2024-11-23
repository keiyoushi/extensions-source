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
class Series(
    val title: String,
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
