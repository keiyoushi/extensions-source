package eu.kanade.tachiyomi.extension.zh.qimanwu

import eu.kanade.tachiyomi.multisrc.mccms.Intl
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSWeb
import eu.kanade.tachiyomi.multisrc.mccms.pcHeaders
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.IOException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Qimanwu : MCCMSWeb() {

    override val config = MCCMSConfig(encryptedPageList = true)

    // The image host refuses requests without the site as Referer
    override val imageFetch get() = Fetch.IMAGE.from("/")

    override fun parseListing(document: Document): MangasPage {
        val mangas = document.select(simpleMangaSelector()).map(::simpleMangaFromElement)
        val hasNextPage = document.selectFirst(".divpage a.next[href^=/], .pager a.next[href^=/]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun simpleMangaSelector() = ".searchResult .list > .item, .bookList_2 > .item"

    override fun simpleMangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst(".title a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        author = element.selectFirst(".line:has(.logo_1) > span:last-child")?.text()?.toAuthor()
        genre = element.select(".line:has(.logo_2) a").joinToString { it.text() }.ifEmpty { null }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return super.getSearchMangaList(page, query, filters)
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .addQueryParameter("page", page.toString())
            .build()
        return searchMangaParse(client.get(url, pcHeaders, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.siteDomain() != baseUrl.toHttpUrl().host.siteDomain()) return null
        val path = when (url.pathSegments.firstOrNull()) {
            "comic" -> "/comic/${url.pathSegments[1]}"
            "chapter" -> client.get(url.newBuilder().host(baseUrl.toHttpUrl().host).build(), pcHeaders, Fetch.DOCUMENT).asJsoup()
                .selectFirst("a:has(img.imgFloat_3)")!!.absUrl("href").toHttpUrl().encodedPath
            else -> return null
        }
        val manga = SManga.create().apply { this.url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaPage(manga: SManga): Document {
        val response = client.get(baseUrl + manga.url, pcHeaders, Fetch.DOCUMENT, ensureSuccess = false)
        // Removed series: "漫画已下架"
        if (response.code == 404) throw IOException(response.asJsoup().selectFirst(".message")!!.text())
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        return response.asJsoup()
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val info = document.selectFirst(".comicInfo")!!
        val fields = info.select(".info span.l, .info span.s").associate {
            it.ownText().substringBefore('：').filterNot(Char::isWhitespace) to it
        }
        setUrlWithoutDomain(document.location())
        title = info.selectFirst("p.title")!!.ownText()
        thumbnail_url = info.selectFirst(".cover img")?.absUrl("src")
        author = fields["作者"]?.ownText()?.substringAfter('：')?.toAuthor()
        genre = fields["类别"]?.select("a")?.joinToString { it.text() }?.ifEmpty { null }
        status = when (fields["状态"]?.ownText()?.substringAfter('：')) {
            "连载", "连载中" -> SManga.ONGOING
            "完结", "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(info.selectFirst("p.content")?.textOrNull())
            .build()
    }

    override fun chapterListParse(document: Document): List<SChapter> {
        val chapters = document.select("#chapterList a[href*=/chapter/]").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.text()
            }
        }.asReversed()
        // The site dates only the series' last update
        val updated = document.selectFirst("#chapterList .topBar .fr")?.text()?.substringAfter('：')
        chapters.firstOrNull()?.date_upload = DATE_FORMAT.tryParseDate(updated, TIME_ZONE)
        return chapters
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = fetchMangaPage(manga)
        .select(".otherMore .item").map(::simpleMangaFromElement)
        .filter { it.url != manga.url }

    override suspend fun fetchFilterData(): JsonElement {
        val genres = client.get("$baseUrl/category", pcHeaders, Fetch.DOCUMENT).asJsoup().select(".indexForm a[href*=/tags/]")
            .map { it.text() to it.absUrl("href").toHttpUrl().pathSegments.last() }
        return (listOf(Intl.all to "") + genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<Pair<String, String>>>())

    private fun String.siteDomain() = removePrefix("www.").removePrefix("m.")

    // "☪" is the site's placeholder for no author
    private fun String.toAuthor() = takeIf { it.isNotBlank() && it != "☪" }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        private val TIME_ZONE = ZoneId.of("Asia/Shanghai")
    }
}
