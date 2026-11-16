package eu.kanade.tachiyomi.extension.ja.daysneo

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class DaysNeo : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getWorkList(rankingUrl("all", page))

    override suspend fun getLatestUpdates(page: Int) = getWorkList(rankingUrl("new", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty()
        val themes = filters.firstInstanceOrNull<ThemeFilter>()?.state.orEmpty().filter { it.state }.map { it.id }
        if (query.isBlank() && status.isEmpty() && genre.isEmpty() && themes.isEmpty()) {
            return getWorkList(rankingUrl(filters.firstInstanceOrNull<RankingFilter>()?.value ?: "all", page))
        }
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("sp/search/")
            if (query.isNotBlank()) addQueryParameter("free_word", query.trim())
            if (status.isNotEmpty()) addQueryParameter("status", status)
            if (genre.isNotEmpty()) addQueryParameter("genre_id", genre)
            themes.forEach { addQueryParameter("theme_id[]", it) }
            addQueryParameter("search", "")
            addQueryParameter("page_num", page.toString())
        }.build()
        return getWorkList(url)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter { it.isNotEmpty() && it != "sp" }
        return when (segments.firstOrNull()) {
            "ranking" -> getWorkList(rankingUrl(segments.getOrElse(1) { "all" }, page))
            "search" -> getWorkList(
                baseUrl.toHttpUrl().newBuilder()
                    .addPathSegments("sp/search/")
                    .encodedQuery(url.encodedQuery)
                    .setQueryParameter("page_num", page.toString())
                    .build(),
            )
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val id = WORK_REGEX.find(url.encodedPath)?.groupValues?.get(1) ?: return null
        return getMangaUpdate(SManga.create().apply { this.url = id }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun rankingUrl(type: String, page: Int) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("sp/ranking/$type/")
        .addQueryParameter("page_num", page.toString())
        .build()

    private suspend fun getWorkList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("ul.tile > li").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("strong a[href*=/works/]")!!
                this.url = WORK_REGEX.find(link.attr("href"))!!.groupValues[1]
                title = link.text()
                author = card.selectFirst("p.author a")?.text()
                thumbnail_url = card.selectFirst("div.img img")?.absUrl("src")
                status = when (card.selectFirst("p.icon:not(.ml0) span")?.text()) {
                    "連載中" -> SManga.ONGOING
                    "完結済み" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }
        return MangasPage(mangas, document.selectFirst("li.last a") != null)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/works/${manga.url}.html"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl/sp/works/${manga.url}.html", Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document, manga), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document, manga: SManga) = SManga.create().apply {
        val info = document.selectFirst("div.inner.top")!!
        url = manga.url
        title = info.selectFirst("p.f150")!!.text()
        author = info.selectFirst("p.author a")?.text()
        thumbnail_url = info.selectFirst("div.img img")?.absUrl("src")
        genre = info.select("ul.dspFbx li a span").eachText().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(info.selectFirst("div.p10:not(#favorite) > div:not([class])"), keepNewlines = true)
            .build()
        // Only the lists show whether a work is finished
        status = manga.status
    }

    // Episodes without a link are only shown to editors
    private fun chapterListParse(document: Document): List<SChapter> {
        val contents = document.selectFirst("h3:contains(もくじ) + div")!!
        return contents.select("li").mapNotNull { item ->
            val link = item.selectFirst("strong a[href*=/episode/]") ?: return@mapNotNull null
            val (work, episode) = EPISODE_REGEX.find(link.attr("href"))!!.destructured
            SChapter.create().apply {
                url = "$work/$episode"
                name = link.text()
                date_upload = DATE_FORMAT.tryParseDate(item.selectFirst("p.date")?.text()?.substringAfter('：'), ZONE)
            }
        }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/works/${chapter.url.replace("/", "/episode/")}.html"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl/sp/works/${chapter.url.replace("/", "/episode/")}.html", Fetch.DOCUMENT).asJsoup()
        // The page-by-page reader sets each page from a script, the vertical one has the images in place
        val slides = document.select("script:containsData(#page_)")
            .flatMap { PAGE_REGEX.findAll(it.data()) }
            .map { it.groupValues[1].toInt() to it.groupValues[2] }
            .distinct()
            .sortedBy { it.first }
            .map { it.second }
        val images = slides.ifEmpty { document.select("img.lazy[src*=/work/p_]").map { it.absUrl("src") } }
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private val WORK_REGEX = Regex("""/works/([0-9a-f]{32})""")
        private val EPISODE_REGEX = Regex("""/works/([0-9a-f]{32})/episode/([0-9a-f]{32})""")
        private val PAGE_REGEX = Regex("""#page_' \+ (\d+)\)\.attr\("src", "([^"]+)"\)""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
        private val ZONE = ZoneId.of("Asia/Tokyo")
    }
}
