package eu.kanade.tachiyomi.extension.en.oppaistream

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLDecoder
import java.util.Calendar

@Source
abstract class OppaiStream : KeiSource() {

    private val cdnUrl = "https://myspacecat.pictures"

    // popular
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderByFilter("views")))

    // latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderByFilter("uploaded")))

    // search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.queryParameter("m") ?: return null

        return fetchMangaUpdate(
            manga = SManga.create().apply { this.url = "/manhwa?m=$slug" },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api-search.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("text", query)
            filters.firstInstanceOrNull<OrderByFilter>()?.let {
                addQueryParameter("order", it.selectedValue())
            }
            filters.firstInstanceOrNull<GenreListFilter>()?.let { filter ->
                addQueryParameter("genres", filter.state.filter { it.isIncluded() }.joinToString(",") { it.value })
                addQueryParameter("blacklist", filter.state.filter { it.isExcluded() }.joinToString(",") { it.value })
            }
            addQueryParameter("page", "$page")
            addQueryParameter("limit", "$SEARCH_LIMIT")
        }.build()

        val document = client.get(url).asJsoup()
        val elements = document.select("div.in-grid > a")

        val mangas = elements.map { element ->
            SManga.create().apply {
                thumbnail_url = element.select("img.read-cover").attr("src")
                title = element.select("h3.man-title").text()
                val rawUrl = element.absUrl("href")
                val url = if (rawUrl.contains("/fw?to=")) {
                    URLDecoder.decode(rawUrl.substringAfter("/fw?to="), "UTF-8")
                } else {
                    rawUrl
                }
                setUrlWithoutDomain(url)
            }
        }

        return MangasPage(mangas, elements.size >= SEARCH_LIMIT)
    }

    // manga details + chapter list
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = manga.apply {
            thumbnail_url = document.select(".cover-img").attr("src")
            document.select(".manhwa-info-in").let { info ->
                info.select("h1").run {
                    title = text().substringBeforeLast("By").trim()
                    author = select("a.red").text()
                    artist = author
                }
                genre = info.select(".genres h5").joinToString { it.text() }
                description = info.select(".description").text()
            }
        }

        val chapterList = document.select(".sort-chapters > a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.select("div > h4").text()
                date_upload = element.select("div > h6").text().parseRelativeDate()
            }
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    // page list
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        val slug = chapterUrl.queryParameter("m")
        val chapNo = chapterUrl.queryParameter("c")

        return client.get("$cdnUrl/manhwa/im.php?f-m=$slug&c=$chapNo").asJsoup().select("img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("src"))
        }
    }

    // filters
    override fun getFilterList(data: JsonElement?) = FilterList(
        OrderByFilter(),
        GenreListFilter(getGenreList()),
    )

    // helpers
    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val relativeDate = try {
            this.split(" ")[0].trim().toInt()
        } catch (e: NumberFormatException) {
            return 0L
        }

        return when {
            "second" in this -> now.apply { add(Calendar.SECOND, -relativeDate) }.timeInMillis
            "minute" in this -> now.apply { add(Calendar.MINUTE, -relativeDate) }.timeInMillis
            "hour" in this -> now.apply { add(Calendar.HOUR, -relativeDate) }.timeInMillis
            "day" in this -> now.apply { add(Calendar.DAY_OF_YEAR, -relativeDate) }.timeInMillis
            "week" in this -> now.apply { add(Calendar.WEEK_OF_YEAR, -relativeDate) }.timeInMillis
            "month" in this -> now.apply { add(Calendar.MONTH, -relativeDate) }.timeInMillis
            "year" in this -> now.apply { add(Calendar.YEAR, -relativeDate) }.timeInMillis
            else -> 0L
        }
    }

    companion object {
        const val SEARCH_LIMIT = 36
    }
}
