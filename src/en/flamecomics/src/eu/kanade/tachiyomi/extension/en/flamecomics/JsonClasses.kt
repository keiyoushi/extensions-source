package eu.kanade.tachiyomi.extension.en.flamecomics

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy

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
    val altTitles: String,
    val description: String,
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

public val json: Json by injectLazy()

public inline fun <reified T> getJsonData(document: Document?): T? {
    val jsonData = document?.getElementById("__NEXT_DATA__")?.data() ?: return null
    return json.decodeFromString<T>(jsonData)
}
