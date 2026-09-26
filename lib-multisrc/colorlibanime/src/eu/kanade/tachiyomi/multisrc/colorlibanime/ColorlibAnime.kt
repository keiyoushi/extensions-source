package eu.kanade.tachiyomi.multisrc.colorlibanime

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ColorlibAnime : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    private val timeRegex = Regex("""Date\((\d+)\)""")

    private fun Element.toThumbnail(): String = this.select(".set-bg").attr("abs:data-setbg").substringBeforeLast("?")

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderFilter(0)))

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderFilter(1)))

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", filters.firstInstanceOrNull<OrderFilter>()?.toUriPart() ?: "view")
            addQueryParameter("search", query)
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select(".product__page__content > [style]:has(.col-6) .product__item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.select("a.img-link").attr("abs:href"))
                title = element.select("h5").text()
                thumbnail_url = element.toThumbnail()
            }
        }

        val hasNextPage = document.selectFirst(".fa-angle-right") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null

        return parseDetails(client.get(url).asJsoup())?.apply { this.url = url.encodedPath }
    }

    // Details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseDetails(document) ?: manga, parseChapters(document))
    }

    private fun parseDetails(document: Document): SManga? {
        val element = document.selectFirst(".anime__details__content") ?: return null

        return SManga.create().apply {
            title = element.select("h3").text()
            author = element.select("h3 + span").text()
            description = element.select("p").text()
            thumbnail_url = element.toThumbnail()
            status = when (element.select("li:contains(status)").text().substringAfter(" ")) {
                "Ongoing" -> SManga.ONGOING
                "Complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters
    private fun parseChapters(doc: Document): List<SChapter> {
        val time = timeRegex.find(doc.select("script:containsData(lastUpdated)").html())
            ?.let { it.groupValues[1].toLong() } ?: 0L

        return doc.select(".anime__details__episodes a")
            .map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.attr("abs:href"))
                    name = element.text()
                    date_upload = 0L
                }
            }
            .apply { firstOrNull()?.date_upload = time }
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".container .read-img > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    // Filters
    override fun getFilterList(data: JsonElement?) = FilterList(
        OrderFilter(),
    )
}
