package eu.kanade.tachiyomi.extension.ko.ddtoon

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
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DDToon : KeiSource() {

    // Old domains redirect to the current one
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOf(
        client.get(baseUrl, Fetch.DOCUMENT).use { it.request.url.toString() },
    )

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(popular = true)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search.php".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query.trim())
                .build()
        } else {
            "$baseUrl/mlist.php".toHttpUrl().newBuilder().apply {
                filters.filterIsInstance<UriPartFilter>().forEach { addQueryParameter(it.param, it.value) }
            }.build()
        }
        // Lists hold at most a few hundred entries, on one page
        val mangas = client.get(url, Fetch.DOCUMENT).asJsoup()
            .select(".fmans_webtoon-list li a[href*=mview1.php]")
            .map { link ->
                SManga.create().apply {
                    this.url = link.absUrl("href").toHttpUrl().queryParameter("id")!!
                    // Titles carry a hidden suffix in a span
                    title = link.selectFirst(".fmans_subject")!!.ownText()
                    thumbnail_url = link.selectFirst("img")?.absUrl("data-original")
                }
            }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.queryParameter("id")?.takeIf { it.all(Char::isDigit) } ?: return null
        val seriesId = when (url.pathSegments.lastOrNull()) {
            "mview1.php" -> id
            "mview2.php" -> client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst("#cookies_toonnum")?.attr("value") ?: return null
            else -> return null
        }
        val manga = SManga.create().apply { this.url = seriesId }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/mview1.php?id=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(manga.url, document), parseChapters(document))
    }

    private fun parseDetails(id: String, document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst(".fmans_text-box")!!
        url = id
        title = info.selectFirst("h1")!!.ownText()
        thumbnail_url = document.selectFirst(".fmans_img-box img")?.absUrl("src")
        description = MarkdownDescription.Builder()
            .description(info.selectFirst(".fmans_txt")?.text()?.takeIf { it != title })
            .build()
        genre = info.selectFirst(".fmans_sub")?.ownText()
            ?.split('/')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString()
            ?.ifEmpty { null }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".fmans_webtoon-bbs-list li a[href*=mview2.php]").map { link ->
        SChapter.create().apply {
            url = link.absUrl("href").toHttpUrl().queryParameter("id")!!
            name = link.selectFirst(".fmans_subject")!!.ownText()
            date_upload = dateFormat.tryParseDate(link.selectFirst(".fmans_date")?.text(), KOREA_ZONE)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/mview2.php?id=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".fmans_image-view img[data-original]").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("data-original"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    }
}
