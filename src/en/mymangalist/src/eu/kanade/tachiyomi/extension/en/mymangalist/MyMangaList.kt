package eu.kanade.tachiyomi.extension.en.mymangalist

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class MyMangaList : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = searchPage(page) {
        addQueryParameter("sort", "popular")
    }

    // "Newest" sorts by the latest chapter update
    override suspend fun getLatestUpdates(page: Int): MangasPage = searchPage(page) {
        addQueryParameter("sort", "newest")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchPage(page) {
        addQueryParameter("q", query.trim())
        filters.filterIsInstance<SelectFilter>().forEach { it.addTo(this) }
    }

    private suspend fun searchPage(page: Int, parameters: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$baseUrl/advanced-search".toHttpUrl().newBuilder().apply {
            addQueryParameter("content_type", "Series")
            parameters()
            addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("[data-results-feed] a.cardish").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst(".card-copy strong")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        // The last page keeps an empty link, and later pages repeat it
        val hasNextPage = document.selectFirst("[data-search-results-shell]")!!.attr("data-next-page-url").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val domain = baseUrl.toHttpUrl().host.substringAfter('.')
        if (url.host != domain && !url.host.endsWith(".$domain")) return null
        val path = url.pathSegments.first()
        val slug = when {
            path.startsWith("t-") -> path.removePrefix("t-")
            path.startsWith("s-") -> path.removePrefix("s-").substringBeforeLast("--")
            else -> return null
        }
        val manga = SManga.create().apply { this.url = "/t-$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // The chapters come from the API, which needs the permalink of the title page, kept in the memo
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val titleUrl = getMangaUrl(manga)
        val permalink = manga.memo["permalink"]?.stringOrNull
        if (permalink != null && !fetchDetails) {
            return SMangaUpdate(manga, fetchChapterList(permalink, titleUrl))
        }
        val details = mangaDetailsParse(client.get(titleUrl, Fetch.DOCUMENT).asJsoup()).apply { url = manga.url }
        return SMangaUpdate(
            details,
            if (fetchChapters) fetchChapterList(details.memo["permalink"]!!.string, titleUrl) else chapters,
        )
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.page-heading")!!.text()
        thumbnail_url = document.selectFirst("section.hero img")?.absUrl("src")
        genre = document.select(".genre-cluster .chip").joinToString { it.text() }.ifEmpty { null }
        author = document.metaValue("Author")
        artist = document.metaValue("Artist")
        status = when (document.metaValue("Status")?.lowercase()) {
            "ongoing", "on-going", "ong" -> SManga.ONGOING
            "completed", "complete", "c" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        // Names lost to a wrong encoding come as question marks only
        val altTitles = document.selectFirst("p.page-lead")?.text()
            ?.removePrefix("Alternative title:")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { name -> name.isNotEmpty() && !name.equals(title, ignoreCase = true) && name.any { it != '?' } }
            .orEmpty()
        description = MarkdownDescription.Builder()
            .info(document.metaValue("Released")?.takeIf { year -> year.all { it.isDigit() } }, document.metaValue("Direction"))
            .description(document.selectFirst("section.panel:has(> h2:containsOwn(Story Overview)) > p"))
            .altTitles(altTitles)
            .build()
        document.selectFirst("#chapterGroups")?.let { groups ->
            memo = buildJsonObject { put("permalink", groups.attr("data-title-permalink")) }
        }
    }

    private fun Document.metaValue(label: String): String? = select(".meta-grid .meta-card")
        .firstOrNull { it.selectFirst("small")?.text() == label }
        ?.selectFirst("strong")
        ?.text()
        ?.takeUnless { it.isEmpty() || it == "-" }

    private suspend fun fetchChapterList(permalink: String, titleUrl: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var offset = 0
        do {
            val url = "$baseUrl/api/titles/chapters.php".toHttpUrl().newBuilder()
                .addQueryParameter("permalink", permalink)
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", CHAPTERS_PER_REQUEST)
                .build()
            val group = client.get(url, Fetch.XHR.from(titleUrl)).parseAs<ChaptersDto>()
            chapters += group.toSChapterList()
            offset = group.nextOffset
        } while (group.hasMore)
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val script = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .selectFirst("script:containsData(MYMANGALIST_READER)")!!
            .data()
        return READER_REGEX.find(script)!!.groupValues[1].parseAs<ReaderDto>().toPageList()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/advanced-search", Fetch.DOCUMENT).asJsoup()
        .select("form select[name]")
        .filterNot { it.attr("name") == "content_type" }
        .map { select ->
            val options = select.select("option")
            SelectData(
                label = select.previousElementSibling()!!.text(),
                parameter = select.attr("name"),
                options = options.map { it.text() to it.attr("value") },
                default = options.indexOfFirst { it.hasAttr("selected") }.coerceAtLeast(0),
            )
        }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<SelectData>>())

    companion object {
        // The API gives at most 50 chapters per request
        private const val CHAPTERS_PER_REQUEST = "50"
        private val READER_REGEX = Regex("""MYMANGALIST_READER = (\{.*\});""")
    }
}
