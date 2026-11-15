package eu.kanade.tachiyomi.extension.th.bocchimanga

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class BocchiManga : KeiSource() {

    override val supportsLatest = false

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = fetchSeries(page, "", emptyList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = fetchSeries(page, query, filters.filterIsInstance<LabelFilter>().flatMap { it.labels })

    private suspend fun fetchSeries(page: Int, query: String, labels: List<String>): MangasPage {
        val terms = (listOf(SERIES_LABEL) + labels).map { "label:\"$it\"" } + listOfNotNull(query.takeIf { it.isNotBlank() }?.trim())
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("feeds/posts/summary")
            .addQueryParameter("alt", "json")
            .addQueryParameter("q", terms.joinToString(" "))
            .addQueryParameter("orderby", "updated")
            .addQueryParameter("max-results", (PAGE_SIZE + 1).toString())
            .addQueryParameter("start-index", ((page - 1) * PAGE_SIZE + 1).toString())
            .build()
        val entries = client.get(url, Fetch.XHR).parseAs<FeedResponse>().feed.entry
        return MangasPage(entries.take(PAGE_SIZE).map { it.toSManga() }, entries.size > PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size != 3 || !url.encodedPath.endsWith(".html")) return null
        val document = client.get(url.mobile(), Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".hartomy-bookmark-btn") != null) {
            return document.toSManga().apply {
                this.url = url.encodedPath
                initialized = true
            }
        }
        // Chapter posts get a breadcrumb per label: the series label, and on some a rating label
        val label = document.select("[itemtype*=BreadcrumbList] [itemprop=itemListElement]:eq(1) [itemprop=item]")
            .map { it.attr("title") }
            .firstOrNull { it.isNotEmpty() && it.toDoubleOrNull() == null }
            ?: return null
        return fetchSeries(1, "", listOf(label)).mangas.firstOrNull()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga).toHttpUrl().mobile(), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            document.toSManga(),
            if (fetchChapters) fetchChapterList(document) else chapters,
        )
    }

    private fun Document.toSManga() = SManga.create().apply {
        val info = selectFirst(".hartomy-bookmark-btn")!!
        val postBody = selectFirst("div.post-body")!!
        title = selectFirst("h1.entry-judul")!!.text()
        thumbnail_url = info.attrOrNull("data-image")?.fullSizeImage()
        author = postBody.selectFirst(".infonya.author > span, .infoTambahan > .authorKomik").infoText()
        artist = postBody.selectFirst(".infonya.artist > span, .infoTambahan > .artist").infoText()
        genre = (listOfNotNull(info.attrOrNull("data-type")) + select(".genreManga a[rel=tag]").map { it.text() })
            .distinct()
            .joinToString()
        status = when (info.attr("data-status")) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .rating(info.attrOrNull("data-skor")?.toDoubleOrNull())
            .description(postBody.selectFirst(".descManga")?.ownTextOrNull() ?: postBody.selectFirst("#sinop")?.textOrNull())
            .field("Serialization", postBody.selectFirst(".infonya.serial > span, .infoTambahan > .serial").infoText())
            .altTitles(
                (postBody.selectFirst(".judulAlt")?.ownTextOrNull() ?: postBody.selectFirst("#alternateTitle")?.textOrNull())
                    ?.split(ALT_TITLE_SEPARATOR)
                    .orEmpty(),
            )
            .build()
    }

    private suspend fun fetchChapterList(document: Document): List<SChapter> {
        val script = document.selectFirst("div.post-body script:containsData(label_chapter =)")!!.data()
        val label = LABEL_REGEX.find(script)!!.groupValues[1]
        val chapters = mutableListOf<SChapter>()
        var startIndex = 1
        do {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("feeds/posts/summary/-")
                .addPathSegment(label)
                .addQueryParameter("alt", "json")
                .addQueryParameter("orderby", "published")
                .addQueryParameter("max-results", CHAPTER_PAGE_SIZE.toString())
                .addQueryParameter("start-index", startIndex.toString())
                .build()
            val feed = client.get(url, Fetch.XHR).parseAs<FeedResponse>().feed
            feed.entry.filterNot { it.isSeries }.mapTo(chapters) { it.toSChapter() }
            startIndex = feed.nextStartIndex
        } while (feed.hasNextPage)
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter).toHttpUrl().mobile(), Fetch.DOCUMENT).asJsoup()
        .select("#readarea img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override suspend fun fetchFilterData(): JsonElement {
        val form = client.get(baseUrl.toHttpUrl().mobile(), Fetch.DOCUMENT).asJsoup().selectFirst("form.filter-box")!!
        fun values(name: String) = form.select("input[name=$name]").map { it.attr("value") }
        return FilterData(values("label"), values("status"), values("type")).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        return FilterList(
            GenreFilter(filterData.genres),
            LabelSelectFilter("สถานะ", filterData.statuses),
            LabelSelectFilter("ประเภท", filterData.types),
        )
    }

    // Blogger redirects the app's mobile user agent to the ?m=1 page
    private fun HttpUrl.mobile() = newBuilder().setQueryParameter("m", "1").build()

    private fun Element?.infoText() = this?.textOrNull()?.takeUnless { it == "-" }

    companion object {
        // Blogger's search feed reports the page size as its total
        private const val PAGE_SIZE = 20

        // Blogger cuts large responses short of max-results
        private const val CHAPTER_PAGE_SIZE = 150

        private val ALT_TITLE_SEPARATOR = Regex("""[,;]\s+""")

        private val LABEL_REGEX = Regex("""label_chapter\s*=\s*"([^"]+)"""")
    }
}
