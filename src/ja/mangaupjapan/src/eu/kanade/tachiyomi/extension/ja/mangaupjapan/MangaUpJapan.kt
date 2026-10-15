package eu.kanade.tachiyomi.extension.ja.mangaupjapan

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class MangaUpJapan : KeiSource() {
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/rankings/1"))

    private fun parseMangaList(response: Response): MangasPage {
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

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/series"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url: HttpUrl = if (query.isNotEmpty()) {
            "$baseUrl/titles".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .build()
        } else {
            val filter = filters.firstInstance<CategoryFilter>()
            "$baseUrl/series/${filter.value}".toHttpUrl()
        }
        return parseMangaList(client.get(url))
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            val document = client.get(baseUrl + manga.url).asJsoup()
            SManga.create().apply {
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
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val response = client.get(baseUrl + manga.url, rscHeaders)
            val titleId = response.request.url.pathSegments[1]
            val bodyText = response.use { it.body.string() }
            val dataLine = bodyText.lines().first { it.contains("\"chapters\":[") }
            val chaptersJson = dataLine.substringAfter("\"chapters\":").substringBefore(",\"currentChapter\"")
            chaptersJson.parseAs<List<ChapterData>>().map { it.toSChapter(titleId) }.reversed()
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private val rscHeaders get() = headers.newBuilder().add("rsc", "1").build()

    private fun chapterPageUrl(chapter: SChapter): String {
        val chapters = "$baseUrl/${chapter.url}".toHttpUrl()
        val titleId = chapters.fragment
        val chapterId = chapters.pathSegments.first()
        return "$baseUrl/titles/$titleId/chapters/$chapterId"
    }

    override fun getChapterUrl(chapter: SChapter): String = chapterPageUrl(chapter)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val bodyText = client.get(chapterPageUrl(chapter), rscHeaders).use { it.body.string() }
        val dataLine = bodyText.lines().first { it.contains("\"pages\":[") }
        val pagesJson = dataLine.substringAfter("\"pages\":").substringBefore("],\"") + "]"
        val pages = pagesJson.parseAs<List<PageData>>()

        return pages.mapNotNull { it.content.value?.imageUrl }.filter { it.isNotEmpty() }.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
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
}
