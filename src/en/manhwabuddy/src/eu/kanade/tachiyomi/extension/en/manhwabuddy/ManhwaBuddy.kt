package eu.kanade.tachiyomi.extension.en.manhwabuddy

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("d [MMMM][MMM] yyyy", Locale.ENGLISH)

@Source
abstract class ManhwaBuddy : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(".item-move").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/page/$page").asJsoup()
        val hasNextPage = document.selectFirst(".next") != null
        val mangas = document.select(".latest-list .latest-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                title = element.selectFirst("h4")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addQueryParameter("s", query)
                addQueryParameter("page", page.toString())
            }.build()
        } else {
            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
            val genre = genreFilter?.toUriPart() ?: ""

            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("genre")
                addPathSegment(genre)
                addPathSegment("page")
                addPathSegment(page.toString())
            }.build()
        }

        val document = client.get(url).asJsoup()
        val hasNextPage = document.selectFirst(".next") != null
        val mangas = document.select(".latest-list .latest-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                title = element.selectFirst("a")!!.attr("title")
                thumbnail_url = element.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = manga.title
            val info = document.selectFirst(".main-info-right")!!
            author = info.selectFirst("li:contains(Author) a")?.text()
            status = when (info.selectFirst("li:contains(Status) span")?.text()) {
                "Ongoing" -> SManga.ONGOING
                "Complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            artist = info.selectFirst("li:contains(Artist) a")?.text()
            genre = info.select("li:contains(Genres) a").joinToString { it.text() }
            description = document.select(".short-desc-content p").joinToString("\n") { it.text() }
        }

        val chapterList = document.select(".chapter-list a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                name = element.selectFirst(".chapter-name")!!.text()
                date_upload = dateFormat.tryParseDate(element.selectFirst(".ct-update")?.text())
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".loading").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filter does not work with text search, reset it before filter"),
        Filter.Separator(),
        GenreFilter(),
    )
}
