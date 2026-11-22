package eu.kanade.tachiyomi.extension.tr.xiaoaitoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class Xiaoaitoon : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/manga/?m_orderby=views".toHttpUrl())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$baseUrl/manga/?m_orderby=latest".toHttpUrl())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            val sort = filters.firstInstance<SortFilter>().value
            return getMangaList("$baseUrl/manga/".toHttpUrl().newBuilder().addQueryParameter("m_orderby", sort).build())
        }
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query.trim())
            .addQueryParameter("post_type", "wp-manga")
            .build()
        return getMangaList(url)
    }

    // Every list of the site fits on one page. Web novels, titled "[Novel]", are text only.
    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("a.release-card").mapNotNull { card ->
            val title = card.selectFirst(".release-title")!!.text()
            if (title.endsWith("[Novel]")) return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                this.title = title
                thumbnail_url = card.selectFirst(".cover img")?.absUrl("src")?.toOriginalSize()
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val listUrl = baseUrl.toHttpUrl().newBuilder().encodedPath(url.encodedPath).encodedQuery(url.encodedQuery).build()
        return when {
            url.pathSegments.first() in LIST_PATHS -> getMangaList(listUrl)
            url.pathSegments.first() == "manga" && url.pathSegments.getOrNull(1).isNullOrEmpty() -> getMangaList(listUrl)
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.first() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val document = client.get("$baseUrl/manga/$slug/", Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".series-format")?.text() == "Web Novel") return null
        return parseDetails(document, "/manga/$slug/").apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document, manga.url), parseChapters(document))
    }

    private fun parseDetails(document: Document, url: String) = SManga.create().apply {
        this.url = url
        title = document.selectFirst("h1.series-title")!!.text()
        thumbnail_url = document.selectFirst(".series-cover img")?.absUrl("src")?.toOriginalSize()
        author = document.select(".series-creator")
            .firstOrNull { it.selectFirst(".lbl")?.text() == "Yazar/Çizer" }
            ?.select("a")
            ?.joinToString { it.text() }
            ?.ifEmpty { null }
        genre = document.select(".series-genres a.genre-tag").joinToString { it.text().removePrefix("#") }.ifEmpty { null }
        status = when (document.selectFirst(".series-stats .st-item")?.text()) {
            "Devam Ediyor" -> SManga.ONGOING
            "Tamamlandı" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".series-summary .summary-text"))
            .altTitles(document.selectFirst(".series-alt")?.text()?.split(",")?.map { it.trim() }.orEmpty())
            .build()
    }

    // Chapters are for members: the site marks them all, and guests see them locked until they log in.
    private fun parseChapters(document: Document): List<SChapter> {
        val loggedIn = document.body().hasClass("logged-in")
        return document.select("ul.chapter-list li > a").map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                val title = link.selectFirst(".ch-name")!!.text()
                name = if (!loggedIn && link.selectFirst(".ch-lock") != null) "🔒 $title" else title
                date_upload = Instant.tryParse(link.selectFirst("time.ch-date")?.attr("datetime"))
            }
        }
    }

    // Guests get a login wall instead of the pages.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("#pages img.wp-manga-chapter-img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // Covers are resized copies ("-768x1024"), sometimes with ".webp" added after their own extension.
    private fun String.toOriginalSize(): String = replace(SIZE_SUFFIX_REGEX, "$1")

    companion object {
        private val LIST_PATHS = listOf("manga-genre", "manga-author", "manga-artist")
        private val SIZE_SUFFIX_REGEX = Regex("""-\d+x\d+(\.(?:jpe?g|png|gif|webp))(?:\.webp)?$""", RegexOption.IGNORE_CASE)
    }
}
