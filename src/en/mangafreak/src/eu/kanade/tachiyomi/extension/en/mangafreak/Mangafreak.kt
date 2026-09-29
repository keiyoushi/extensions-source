package eu.kanade.tachiyomi.extension.en.mangafreak

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Mangafreak : KeiSource() {

    private val floatLetterPattern = Regex("""(\d+)(\.\d+|[a-i]+\b)?""")

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = connectTimeout(1.minutes)
        .readTimeout(1.minutes)
        .retryOnConnectionFailure(true)
        .followRedirects(true)

    private fun mangaFromElement(element: Element, urlSelector: String): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        element.selectFirst(urlSelector)!!.run {
            title = text()
            setUrlWithoutDomain(absUrl("href"))
        }
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/Genre/All/$page").asJsoup()
        val mangas = document.select("div.ranking_item").map { mangaFromElement(it, "a") }
        val hasNextPage = document.select("a.next_p").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) {
            baseUrl
        } else {
            "$baseUrl/Latest_Releases/$page"
        }
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.latest_item, div.latest_releases_item").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.absUrl("src")?.let {
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
                    element.selectFirst("a.name")!!.run {
                        title = text()
                        setUrlWithoutDomain(absUrl("href"))
                    }
                } else {
                    element.selectFirst("a")!!.run {
                        title = text()
                        setUrlWithoutDomain(absUrl("href"))
                    }
                }
            }
        }
        val hasNextPage = document.select("a.next_p").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        val document = client.get(url.build()).asJsoup()
        val mangas = document.select("div.manga_search_item , div.mangaka_search_item")
            .map { mangaFromElement(it, "h3 a, h5 a") }
        return MangasPage(mangas, false)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            thumbnail_url = document.selectFirst("div.manga_series_image img")?.absUrl("src")
            title = document.select("div.manga_series_data h5").text()
            status = when (document.select("div.manga_series_data > div:eq(2)").text().lowercase()) {
                "on-going", "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            author = document.select("div.manga_series_data > div:eq(3)").text()
            artist = document.select("div.manga_series_data > div:eq(4)").text()
            genre = document.select("div.series_sub_genre_list a").joinToString { it.text() }
            description = document.select("div.manga_series_description p").text()
        }

        val chapterList = document.select("div.manga_series_list tr:has(a)").map { element ->
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

                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                date_upload = dateFormat.tryParseDate(element.select("td:eq(1)").text(), ZoneOffset.UTC)
            }
        }.reversed()

        return SMangaUpdate(details, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("img#gohere[src]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filters do not work if search bar is empty"),
        GenreFilter(getGenreList()),
        TypeFilter(),
        StatusFilter(),
    )
}
