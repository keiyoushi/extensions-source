package eu.kanade.tachiyomi.extension.en.manhwa18

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH)

@Source
abstract class Manhwa18 : KeiSource() {

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$baseUrl/tim-kiem?sort=top&page=$page".toHttpUrl())

    private suspend fun parseMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select(".thumb-item-flow").map { element ->
            SManga.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.attr("abs:href"))
                title = element.selectFirst(".series-title a")!!.text()
                thumbnail_url = element.selectFirst(".lazy-bg")?.attr("data-bg")
                    ?: element.selectFirst(".img-in-ratio")?.attr("style")?.substringAfter("url('")?.substringBefore("')")
            }
        }
        val hasNextPage = document.selectFirst(".pagination_wrap a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$baseUrl/tim-kiem?sort=update&page=$page".toHttpUrl())

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        url.addQueryParameter("sort", sortFilter?.selectedValue() ?: "update")

        if (statusFilter != null && statusFilter.state != 0) {
            url.addQueryParameter("status", statusFilter.selectedValue())
        }

        if (genreFilter != null) {
            val included = genreFilter.state.filter { it.state }.joinToString(",") { it.id }
            if (included.isNotEmpty()) {
                url.addQueryParameter("accept_genres", included)
            }
        }

        url.addQueryParameter("page", page.toString())

        return parseMangaList(url.build())
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
            url = manga.url
            title = document.selectFirst(".series-name a, .au-bento h1, .au-crumb a:last-child")!!.text()
            thumbnail_url = document.selectFirst(".series-cover .img-in-ratio")?.attr("style")?.substringAfter("url('")?.substringBefore("')")
            description = document.selectFirst(".summary-content")?.text()

            val infoItems = document.select(".series-information .info-item")
            for (item in infoItems) {
                val name = item.selectFirst(".info-name")?.text() ?: continue
                val value = item.selectFirst(".info-value")?.text() ?: continue

                when {
                    name.contains("Author", true) -> author = value
                    name.contains("Genre", true) -> genre = item.select(".info-value a").joinToString { it.text() }
                    name.contains("Status", true) -> status = when (value.lowercase().replace(" ", "")) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        "onhold" -> SManga.ON_HIATUS
                        else -> SManga.UNKNOWN
                    }
                }
            }

            // Fallback for author field if not strictly displayed in info items
            if (author.isNullOrEmpty()) {
                author = document.selectFirst(".fantrans-value a")?.text()
            }
        }

        val chapterList = document.select("div.au-chgrid a.au-chtile, ul.list-chapters > a").map { a ->
            SChapter.create().apply {
                setUrlWithoutDomain(a.attr("abs:href"))
                name = a.attr("data-name").ifEmpty { a.attr("title") }

                val timeStr = a.selectFirst(".au-chtile-date")?.text()?.substringAfter("·")?.trim()
                    ?: a.selectFirst(".chapter-time")?.text()?.substringAfter("-")?.trim()
                date_upload = dateFormat.tryParseDate(timeStr)
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#chapter-content img.lazy").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:data-src"))
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter(genreList),
    )
}
