package eu.kanade.tachiyomi.extension.ja.hachiraw

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Hachiraw : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(
        page,
        "",
        FilterList(SortFilter(2)),
    )

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(
        page,
        "",
        FilterList(SortFilter(0)),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        val mangaUrl = "/manga/$slug"
        return mangaDetailsParse(client.get(baseUrl + mangaUrl).asJsoup()).apply {
            this.url = mangaUrl
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("list-manga")

            if (query.isNotEmpty()) {
                addQueryParameter("search", query)
            } else if (genreFilter != null && genreFilter.state != 0) {
                setPathSegment(0, "manga-list")
                addPathSegment(genreFilter.items[genreFilter.state].id)
            }

            if (page > 1) {
                addPathSegment(page.toString())
            }

            if (sortFilter != null) {
                addQueryParameter("order_by", sortFilter.items[sortFilter.state].id)
            }
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select("div.ng-scope > div.top-15").map { element ->
            SManga.create().apply {
                element.selectFirst("a.ng-binding.SeriesName")!!.let {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.text()
                }
                thumbnail_url = element.selectFirst("img.img-fluid")?.absUrl("src")?.fixCoverUrl()
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li:contains(→)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val chapterList = document.select("a.ChapterLink").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.selectFirst("span")!!.text()
                date_upload = dateFormat.tryParseDate(
                    element.selectFirst("span.float-right")?.text(),
                )
            }
        }

        return SMangaUpdate(mangaDetailsParse(document), chapterList)
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val row = document.selectFirst("div.BoxBody > div.row")!!

        title = row.selectFirst("h1")!!.text()
        author = row.selectFirst("li.list-group-item:contains(著者)")?.ownText()
        genre = row.select("li.list-group-item:contains(ジャンル) a").joinToString { it.text() }
        thumbnail_url = row.selectFirst("img.img-fluid")?.absUrl("src")?.fixCoverUrl()
        description = buildString {
            row.select("li.list-group-item:has(span.mlabel)").forEach {
                val key = it.selectFirst("span")!!.text().removeSuffix(":")
                val value = it.ownText()

                if (key == "著者" || key == "ジャンル" || value.isEmpty() || value == "-") {
                    return@forEach
                }

                append(key)
                append(": ")
                appendLine(value)
            }

            val desc = row.select("div.Content").text()

            if (desc.isNotEmpty()) {
                appendLine()
                append(desc)
            }
        }.trim()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup().select("#TopPage img").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("src"))
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("タイトルで検索する場合、ジャンルフィルターは無視されます"),
        Filter.Separator(),
        SortFilter(),
        GenreFilter(),
    )
}

private val dateFormat = DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ROOT)

// Browse/details pages still point covers at an i0.wp.com proxy of cdn.kumaraw.com that now 403s; the homepage serves the same files from cdn.hachiraw.net
private fun String.fixCoverUrl() = replace("https://i0.wp.com/cdn.kumaraw.com/", "https://cdn.hachiraw.net/")
