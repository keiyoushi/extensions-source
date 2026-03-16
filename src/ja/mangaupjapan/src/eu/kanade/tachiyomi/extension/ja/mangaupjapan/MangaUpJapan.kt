package eu.kanade.tachiyomi.extension.ja.mangaupjapan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class MangaUpJapan : HttpSource() {
    override val name = "Manga UP! (Japan)"
    override val baseUrl = "https://www.manga-up.com"
    override val lang = "ja"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/rankings/1", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.grid > a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst("div.line-clamp-2")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/series", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/titles".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val url = "$baseUrl/series/${filter.value}".toHttpUrl()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2")!!.text()
            thumbnail_url = document.selectFirst("section > img")?.absUrl("src")
            author = document.select("div.flex.flex-col.gap-2xsmall > div").joinToString { it.text() }
            description = document.selectFirst("h2:contains(あらすじ) + div")?.text()
            genre = document.select("a[href*=/genres/]").joinToString { it.text() }
            status = when {
                document.selectFirst("div:contains(完結)") != null -> SManga.COMPLETED
                document.selectFirst("div:contains(更新)") != null -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, super.headersBuilder().add("rsc", "1").build())

    override fun chapterListParse(response: Response): List<SChapter> {
        val bodyText = response.body.string()
        val dataLine = bodyText.lines().first { it.contains("\"chapters\":[") }
        val chaptersJson = dataLine.substringAfter("\"chapters\":").substringBefore(",\"currentChapter\"")
        val titleId = response.request.url.pathSegments[1]
        val results = chaptersJson.parseAs<List<ChapterData>>()
        return results.map { it.toSChapter(titleId) }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapters = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = chapters.fragment
        val chapterId = chapters.pathSegments.first()
        val url = "$baseUrl/titles/$titleId/chapters/$chapterId"
        return GET(url, super.headersBuilder().add("rsc", "1").build())
    }

    override fun pageListParse(response: Response): List<Page> {
        val bodyText = response.body.string()
        val dataLine = bodyText.lines().first { it.contains("\"pages\":[") }
        val pagesJson = dataLine.substringAfter("\"pages\":").substringBefore("],\"") + "]"
        val pages = pagesJson.parseAs<List<PageData>>()

        return pages.mapNotNull { it.content.value?.imageUrl }.filter { it.isNotEmpty() }.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun getFilterList() = FilterList(
        CategoryFilter(),
    )

    private class CategoryFilter :
        SelectFilter(
            "Category",
            arrayOf(
                Pair("月曜日", "mon"),
                Pair("火曜日", "tue"),
                Pair("水曜日", "wed"),
                Pair("木曜日", "thu"),
                Pair("金曜日", "fri"),
                Pair("土曜日", "sat"),
                Pair("日曜日", "sun"),
                Pair("完", "end"),
            ),
        )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val value: String
            get() = vals[state].second
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
