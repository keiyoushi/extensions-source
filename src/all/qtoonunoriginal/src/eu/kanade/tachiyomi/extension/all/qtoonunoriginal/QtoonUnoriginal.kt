package eu.kanade.tachiyomi.extension.all.qtoonunoriginal

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class QtoonUnoriginal : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    // The site page each listing page starts from: see getListing
    private val listingPages = ConcurrentHashMap<String, Int>()

    override suspend fun getPopularManga(page: Int) = getListing(browseUrl("popular"), page)

    override suspend fun getLatestUpdates(page: Int) = getListing(browseUrl("latest"), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = browseUrl(filters.firstInstance<SortFilter>().value).newBuilder()
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query.trim())
                filters.firstInstanceOrNull<GenreFilter>()?.value?.let { addQueryParameter("genre", it) }
            }
            .build()
        return getListing(url, page)
    }

    private fun browseUrl(sort: String) = "$baseUrl/browse".toHttpUrl().newBuilder()
        .addQueryParameter("sort", sort)
        .build()

    // The site's listings can't be filtered by language: they have every series, and a card shows the flags of its
    // chapters' languages. Pages of the site without a card in the source's language are skipped (French has about
    // one series in a hundred), and the next listing page goes on after them.
    private suspend fun getListing(url: HttpUrl, page: Int): MangasPage {
        val flag = ".cover-lang-flags .fi-${FLAGS.getValue(lang)}"
        if (page == 1) listingPages.clear()
        var sitePage = listingPages["$url $page"] ?: page
        while (true) {
            val cards = client.get(url.newBuilder().addQueryParameter("page", sitePage.toString()).build(), Fetch.DOCUMENT)
                .asJsoup()
                .select("#genre-media-flow .manga-item")
            val mangas = cards.filter { it.selectFirst(flag) != null }.map(::seriesCard)
            val hasNextPage = cards.size >= PAGE_SIZE
            sitePage++
            if (mangas.isNotEmpty() || !hasNextPage) {
                listingPages["$url ${page + 1}"] = sitePage
                return MangasPage(mangas, hasNextPage)
            }
        }
    }

    private fun seriesCard(card: Element) = SManga.create().apply {
        setUrlWithoutDomain(card.selectFirst("a")!!.absUrl("href"))
        title = card.selectFirst(".mangaName")!!.text()
        thumbnail_url = card.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val seriesUrl = when (url.pathSegments[0]) {
            "read" -> client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst("a.rdr-back")!!.absUrl("href").toHttpUrl()
            in SERIES_PATHS -> url
            else -> return null
        }
        val document = getSeriesPage(seriesUrl)
        if (document.select("#episode-list li[data-langs]").none { lang in it.attr("data-langs").split(',') }) return null
        return parseSeries(document).apply {
            setUrlWithoutDomain(document.selectFirst("link[rel=canonical]")!!.absUrl("href"))
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = getSeriesPage(getMangaUrl(manga).toHttpUrl())
        return SMangaUpdate(parseSeries(document).apply { url = manga.url }, parseChapters(document))
    }

    // With `lang`, each chapter row shows the name and date of its chapter in that language, when it has one
    private suspend fun getSeriesPage(url: HttpUrl) = client.get(url.newBuilder().setQueryParameter("lang", lang).build(), Fetch.DOCUMENT).asJsoup()

    private fun parseSeries(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.title")!!.text()
        thumbnail_url = document.selectFirst("img.image")?.absUrl("src")
        author = document.select("a.detail-author-link").joinToString { it.text() }.ifEmpty { null }
        genre = document.select(".detail-meta-chip[class*=detail-meta-chip--], ul.tag a").map { it.text() }.distinct().joinToString().ifEmpty { null }
        status = when {
            document.selectFirst(".detail-meta-chip.chip-ongoing") != null -> SManga.ONGOING
            document.selectFirst(".detail-meta-chip.chip-done") != null -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#syn-clamp"))
            .altTitles(document.selectFirst("#alt-titles")?.textNodes().orEmpty().map { it.text().trim() }.filter { it.isNotEmpty() })
            .build()
    }

    private fun parseChapters(document: Document) = document.select("#episode-list > li[data-ep-num]").mapNotNull { row ->
        val link = row.selectFirst(".ch-langs a[data-lang=\"$lang\"]") ?: return@mapNotNull null
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = row.selectFirst("h3.name")!!.text()
            chapter_number = row.attr("data-ep-num").toFloatOrNull() ?: -1f
            date_upload = DateTimeFormatter.ISO_LOCAL_DATE.tryParseDate(row.selectFirst("p.time")?.text())
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#rdr-panels img[data-src]")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("data-src")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/browse", Fetch.DOCUMENT).asJsoup()
        .select("a.banner-item[href*=genre=]")
        .associate { it.absUrl("href").toHttpUrl().queryParameter("genre").orEmpty() to it.text() }
        .filterKeys { it.isNotEmpty() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Map<String, String>>() ?: return FilterList(SortFilter())
        return FilterList(SortFilter(), GenreFilter(listOf("All" to "") + genres.map { (slug, name) -> name to slug }))
    }

    companion object {
        private const val PAGE_SIZE = 48

        // The flags the cards show for each chapter language
        private val FLAGS = mapOf("en" to "us", "es" to "es", "fr" to "fr", "pt-BR" to "br")

        private val SERIES_PATHS = setOf("manga", "manhwa", "manhua", "novel", "comic", "one_shot")
    }
}
