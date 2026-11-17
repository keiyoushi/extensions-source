package eu.kanade.tachiyomi.extension.zh.bookwalkertw

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.minutes

@Source
abstract class BookWalkerTaiwan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(WafInterceptor { client })
        // A burst of about 20 searches gets a captcha; one every 4 seconds goes through
        rateLimit(permits = 15, period = 1.minutes) { it.encodedPath == "/search" }
        addInterceptor(PublusInterceptor())
    }

    override suspend fun getPopularManga(page: Int): MangasPage = searchPage(searchUrl(page, "rank").build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchPage(searchUrl(page, "sell_desc").build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchUrl(page, filters.firstInstance<SortFilter>().value!!).apply {
            if (query.isNotBlank()) addQueryParameter("w", query.trim())
            filters.firstInstance<CategoryFilter>().value?.let { addQueryParameter("s", it) }
            if (filters.firstInstance<SerialFilter>().state) addQueryParameter("tp", "13")
            if (filters.firstInstance<AllAgesFilter>().state) addQueryParameter("restricted", "2")
        }
        return searchPage(url.build())
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return MangasPage(emptyList(), false)
        // Author, tag and label pages are searches too
        if (url.pathSegments.first() == "search" && url.queryParameter("series") == null) {
            return searchPage(url.newBuilder().setQueryParameter("page", page.toString()).build())
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        val id = segments.getOrNull(1)
        return when (segments.first()) {
            "search" -> url.queryParameter("series")?.let {
                getMangaUpdate(SManga.create().apply { this.url = seriesUrl(it) }, emptyList(), fetchDetails = true, fetchChapters = false).manga
            }
            "serial" -> id?.let { pageManga("$baseUrl/serial/$it") }
            "product", "browserViewer" -> id?.takeIf { it.all(Char::isDigit) }?.let { pageManga("$baseUrl/product/$it") }
            else -> null
        }
    }

    // A book's page, or the page of the serial it belongs to
    private suspend fun pageManga(url: String): SManga {
        val response = client.get(url, Fetch.DOCUMENT)
        val document = response.asJsoup()
        val (type, id) = response.request.url.pathSegments
        val manga = if (type == "serial") {
            document.toSerialManga(seriesUrl(id))
        } else {
            // A volume stands for its series, described by its first volume
            val product = document.product()
            val firstId = product.volumes.firstOrNull()?.id?.toString()
            val first = if (firstId == null || firstId == id) product else product(firstId)
            first.toSManga(if (product.isSeries) seriesUrl(product.seriesId.toString()) else "/product/$id")
        }
        return manga.apply { initialized = true }
    }

    private fun searchUrl(page: Int, order: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("search")
        .addQueryParameter("m", "2")
        .addQueryParameter("series_display", "1")
        .addQueryParameter("order", order)
        .addQueryParameter("page", page.toString())

    private suspend fun searchPage(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(CARD).map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.selectFirst("a")!!.absUrl("href"))
                title = card.selectFirst("h4")!!.text()
                thumbnail_url = card.selectFirst("img[data-src]")?.absUrl("data-src")?.let(::bigCover)
            }
        }
        // The last page has no next link; the pages after it repeat it
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    // The lists show a small cover, masked for 18+ books
    private fun bigCover(url: String): String {
        val cover = url.toHttpUrl()
        val id = cover.pathSegments[1]
        return cover.newBuilder().setPathSegment(2, "zoom_big_$id.jpg").build().toString()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga).toHttpUrl()
        val seriesId = url.queryParameter("series")
        if (seriesId == null) {
            val id = url.pathSegments[1]
            val product = product(id)
            return SMangaUpdate(product.toSManga(manga.url), listOf(product.toSChapter(id.toInt())))
        }

        val response = client.get(url, Fetch.DOCUMENT)
        val document = response.asJsoup()
        // Serials have a page of their own, with their episodes behind an API
        if (response.request.url.pathSegments.first() == "serial") {
            return SMangaUpdate(
                manga = document.toSerialManga(manga.url),
                chapters = if (fetchChapters) episodes(seriesId) else chapters,
            )
        }

        // Any volume lists them all; the first one describes the series
        val volumeId = document.selectFirst(CARD)!!.selectFirst("a")!!.absUrl("href").toHttpUrl().pathSegments[1]
        val volume = product(volumeId)
        val firstId = volume.volumes.first().id
        val first = if (firstId.toString() == volumeId) volume else product(firstId.toString())
        val details = first.toSManga(manga.url)
        return SMangaUpdate(details, first.volumes.reversed().map { it.toSChapter(details.title) })
    }

    private suspend fun product(id: String): Product = client.get("$baseUrl/product/$id", Fetch.DOCUMENT).asJsoup().product()

    private fun Document.product(): Product = selectFirst("#app")!!.attr("data-page").parseAs<ProductPage>().props.productData

    private fun Document.toSerialManga(url: String) = SManga.create().apply {
        this.url = url
        title = selectFirst("h1.bookName")!!.text()
        thumbnail_url = selectFirst(".mainDescCover img")?.absUrl("src")?.replace("_mask.jpg", ".jpg")
        val credits = select("ul.bookDetailList li").associate { li ->
            li.ownText().substringBefore('：') to li.select("a").joinToString { it.text() }
        }
        author = listOfNotNull(credits["作者"], credits["原作"], credits["編劇"]).joinToString().ifEmpty { null }
        artist = listOfNotNull(credits["漫畫"], credits["插畫"]).joinToString().ifEmpty { null }
        genre = select(".tagKeywordBox a.tagKeyword").joinToString { it.text() }.ifEmpty { null }
        status = when (selectFirst(".bookTags span")?.text()) {
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(selectFirst(".bookIntroConBox .bookIntroCon"))
            .field("譯者", credits["譯者"])
            .field("出版社", credits["出版社"])
            .field("連載時間", selectFirst(".bookUpdateTime")?.text())
            .build()
    }

    private suspend fun episodes(seriesId: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val url = "$baseUrl/serial/next/$seriesId".toHttpUrl().newBuilder()
                .addQueryParameter("order", "desc")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("page_type", "pc")
                .build()
            val result = client.get(url, Fetch.XHR.from("/serial/$seriesId")).parseAs<EpisodePage>()
            result.data.mapTo(chapters) { it.toSChapter() }
        } while (page++ < result.lastPage)
        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val viewer = chapter.memo["viewer"]?.stringOrNull ?: return emptyList()
        val id = chapter.url.substringAfterLast('/')
        val viewerUrl = client.get("$baseUrl/browserViewer/$id/$viewer", Fetch.DOCUMENT).use { it.request.url }
        // A login page when the book needs one: not owned, or 18+
        val cid = viewerUrl.queryParameter("cid") ?: return emptyList()
        // The viewer's own settings name its content API
        val config = client.get(viewerUrl.resolve("js/config.js")!!, Fetch.SCRIPT).body.string()
        val api = SERVER_DOMAIN.find(config)!!.groupValues[1].toHttpUrl().newBuilder()
            .addPathSegment("c")
            .addQueryParameter("cid", cid)
            .addQueryParameter("BID", "0")
            .build()
        val content = client.get(api, Fetch.XHR.from(viewerUrl.toString())).parseAs<PublusContent>()
        val contentUrl = content.url ?: return emptyList()
        return client.fetchPages(contentUrl, content.authInfo?.toAuth(bid = "0", includeBookAuth = viewer == "read"))
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    private fun seriesUrl(id: String) = "/search?series=$id"

    companion object {
        // Phones and computers get different markup for the same lists
        private const val CARD = ".bw_searchbox, .bwbookitem"
        private val SERVER_DOMAIN = Regex("""serverDomain = !location\.port \? '([^']+)'""")
    }
}
