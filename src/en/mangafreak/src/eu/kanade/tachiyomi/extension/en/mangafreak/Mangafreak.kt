package eu.kanade.tachiyomi.extension.en.mangafreak

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Mangafreak : HttpSource() {
    override val name: String = "Mangafreak"

    override val lang: String = "en"

    override val baseUrl: String = "https://ww2.mangafreak.me"

    override val supportsLatest: Boolean = true

    private val floatLetterPattern = Regex("""(\d+)(\.\d+|[a-i]+\b)?""")

    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private fun mangaFromElement(element: Element, urlSelector: String): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src")
        element.select(urlSelector).apply {
            title = text()
            url = attr("href")
        }
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/Genre/All/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.ranking_item").map { mangaFromElement(it, "a") }
        val hasNextPage = document.select("a.next_p").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) {
            baseUrl
        } else {
            "$baseUrl/Latest_Releases/$page"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.latest_item, div.latest_releases_item").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")?.let {
                    val url = it.toHttpUrlOrNull()
                    if (url != null && url.pathSegments.firstOrNull() == "mini_images" && url.pathSegments.size >= 2) {
                        val slug = url.pathSegments[1]
                        url.newBuilder()
                            .encodedPath("/")
                            .addPathSegment("manga_images")
                            .addPathSegment("$slug.jpg")
                            .build()
                            .toString()
                    } else {
                        it
                    }
                }

                if (element.hasClass("latest_item")) {
                    element.select("a.name").apply {
                        title = text()
                        url = attr("href")
                    }
                } else {
                    element.select("a").apply {
                        title = first()!!.text()
                        url = attr("href")
                    }
                }
            }
        }
        val hasNextPage = document.select("a.next_p").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addPathSegments("Find/$query")
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val genres = filter.state.joinToString("") {
                        when (it.state) {
                            Filter.TriState.STATE_IGNORE -> "0"
                            Filter.TriState.STATE_INCLUDE -> "1"
                            Filter.TriState.STATE_EXCLUDE -> "2"
                            else -> "0"
                        }
                    }
                    url.addPathSegments("Genre/$genres")
                }

                is StatusFilter -> url.addPathSegments("Status/${filter.toUriPart()}")

                is TypeFilter -> url.addPathSegments("Type/${filter.toUriPart()}")

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.manga_search_item , div.mangaka_search_item").map { mangaFromElement(it, "h3 a, h5 a") }
        return MangasPage(mangas, false)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            thumbnail_url = document.select("div.manga_series_image img").attr("abs:src")
            title = document.select("div.manga_series_data h5").text()
            status = when (document.select("div.manga_series_data > div:eq(2)").text()) {
                "ON-GOING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            author = document.select("div.manga_series_data > div:eq(3)").text()
            artist = document.select("div.manga_series_data > div:eq(4)").text()
            genre = document.select("div.series_sub_genre_list a").joinToString { it.text() }
            description = document.select("div.manga_series_description p").text()
        }
    }

    // Chapter

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.manga_series_list tr:has(a)").map { element ->
            SChapter.create().apply {
                name = element.select("td:eq(0)").text()

                val match = floatLetterPattern.find(name)
                chapter_number = if (match == null) {
                    -1f
                } else {
                    if (match.groupValues[2].isEmpty() || match.groupValues[2][0] == '.') {
                        match.value.toFloat()
                    } else {
                        val p2 = buildString {
                            append("0.")
                            for (x in match.groupValues[2]) {
                                append(x.code - 'a'.code + 1)
                            }
                        }.toFloat()
                        val p1 = match.groupValues[1].toFloat()
                        p1 + p2
                    }
                }

                setUrlWithoutDomain(element.select("a").attr("href"))
                date_upload = dateFormat.tryParse(element.select("td:eq(1)").text())
            }
        }.reversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img#gohere[src]").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filter

    override fun getFilterList() = FilterList(
        Filter.Header("Filters do not work if search bar is empty"),
        GenreFilter(getGenreList()),
        TypeFilter(),
        StatusFilter(),
    )
}
