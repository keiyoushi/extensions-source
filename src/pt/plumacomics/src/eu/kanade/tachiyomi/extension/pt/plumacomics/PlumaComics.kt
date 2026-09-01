package eu.kanade.tachiyomi.extension.pt.plumacomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class PlumaComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            SelectFilter(vals = listOf("" to "popular"), query = "sort"),
        ),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/obras".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        filters.filterIsInstance<SelectFilter>().forEach {
            url.addQueryParameter(it.query, it.selected)
        }

        val dto = client.get(url.build()).parseAs<Mangas>()

        return MangasPage(dto.series.map { it.toSManga(baseUrl) }, hasNextPage = dto.page < dto.totalPages)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/title/${manga.memo["slug"]!!.string}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.filter(String::isNotEmpty).size != 2 || !baseUrl.endsWith(url.host, ignoreCase = true)) {
            return null
        }

        val document = client.get(url).asJsoup()
        val dto = document.extractNextJs<Series>() ?: return null

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            this.url = dto.seriesId.toString()
            memo = buildJsonObject {
                put("slug", dto.seriesSlug)
            }
        }
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        manga.apply {
            title = document.selectFirst("meta[property*=title]")!!.text().substringBeforeLast("|")
            thumbnail_url = document.selectFirst("img.object-cover")?.absUrl("src")
            description = document.selectFirst("div p.text-neutral-300.text-sm")?.text()
            genre = document.select("a[href*='?genre=']").joinToString { it.text() }
        }

        val chapters = document.extractNextJs<ChapterList>()!!.chapters.map { it.toSChapter() }

        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/api/viewer/bootstrap?c=${chapter.url}")
        val pages = response.parseAs<PagesList>()
        return pages.pages.map { page ->
            Page(page.i, imageUrl = page.u.trim('/'))
        }
    }

    private class SelectFilter(
        displayName: String = "",
        private val vals: List<Pair<String, String>> = emptyList(),
        val query: String = "",
    ) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), 0) {
        val selected: String get() = vals[state].second
    }
}
