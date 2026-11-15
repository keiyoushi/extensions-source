package eu.kanade.tachiyomi.extension.ar.peachbl

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class PeachBL : KeiSource() {

    // The image script throttles readers who go too fast
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 3) { it.encodedPath.endsWith("/img.php") }

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(page, "most_viewed")

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(page, "latest_chapter")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
        return mangaListParse(page, filters.firstInstanceOrNull<SortFilter>()?.selected() ?: "most_viewed") {
            if (query.isNotBlank()) addQueryParameter("q", query)
            filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("type[]", it.value) }
            filters.firstInstanceOrNull<StatusFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("status[]", it.value) }
            filters.firstInstanceOrNull<ContentFilter>()?.selected()?.takeIf(String::isNotEmpty)?.let { addQueryParameter("content", it) }
            genres.filter { it.isIncluded() }.takeIf { it.isNotEmpty() }?.let { included -> addQueryParameter("genre", included.joinToString(",") { it.slug }) }
            genres.filter { it.isExcluded() }.takeIf { it.isNotEmpty() }?.let { excluded -> addQueryParameter("genre_ex", excluded.joinToString(",") { it.slug }) }
        }
    }

    private suspend fun mangaListParse(page: Int, sort: String, params: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val url = "$baseUrl/webtoon/".toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("sort", sort)
            params()
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.webtoon-card").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.selectFirst("a.cover-link")!!.absUrl("href"))
                title = card.selectFirst(".title-text")!!.text()
                thumbnail_url = card.selectFirst("img.cover-image")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst(".rf-pagination a.next") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val seriesUrl = when (url.pathSegments.firstOrNull()) {
            "webtoon" -> url
            // A chapter page links back to its series
            "chapter" -> client.get(url, Fetch.DOCUMENT).asJsoup()
                .selectFirst("a[href*='/webtoon/']:has(.ch-back-meta)")?.absUrl("href")?.toHttpUrl() ?: return null
            else -> return null
        }
        val document = client.get(seriesUrl, Fetch.DOCUMENT).asJsoup()
        return mangaDetailsParse(document).apply { setUrlWithoutDomain(seriesUrl.toString()) }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = document.select("ul.chapter-list a.chapter-link").map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = buildString {
                    append(link.selectFirst(".chapter-number")!!.text())
                    link.selectFirst(".chapter-type")?.text()?.let { append(" ($it)") }
                }
                date_upload = DATE_FORMAT.tryParseDate(link.selectFirst(".chapter-date")?.text(), ZoneOffset.UTC)
            }
        }
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, chapterList)
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.wt-title")!!.text()
        thumbnail_url = document.selectFirst("img[fifulocal-featured]")?.absUrl("src")
        val details = document.select(".wt-detail-row").mapNotNull { row ->
            val key = row.selectFirst(".wt-detail-key")?.text() ?: return@mapNotNull null
            val value = row.selectFirst(".wt-detail-val") ?: return@mapNotNull null
            key to value
        }.toMap()
        genre = details["التصنيفات"]?.select("a")?.eachText()?.joinToString()
        artist = details["الرسّام"]?.text()
        author = details["الكاتب"]?.text()
        status = when (details["الحالة"]?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "stopped" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        // The site joins the alternative names with spaces, so they can't be split into a list
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".wt-synopsis-body"))
            .field("أسماء أخرى", document.selectFirst(".wt-alt[data-full]")?.attr("data-full"))
            .build()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val config = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("script:containsData(RAWI_IMG)")!!.data()
            .let { READER_CONFIG_REGEX.find(it)!!.groupValues[1] }
            .parseAs<ReaderConfig>()

        // Like the site's own reader: a fresh token for the chapter, then its signed image urls (valid for about 2 hours)
        val token = client.post(
            "${config.ajax}?action=rawi_ch_token",
            Fetch.XHR.from(chapterUrl),
            FormBody.Builder().add("action", "rawi_ch_token").add("chapter_id", config.cid.toString()).build(),
        ).parseAs<AjaxResponse<TokenDto>>().data

        val images = client.post(
            "${config.ajax}?action=rawi_ch_images",
            Fetch.XHR.from(chapterUrl),
            FormBody.Builder()
                .add("action", "rawi_ch_images")
                .add("chapter_id", config.cid.toString())
                .add("exp", token.exp.toString())
                .add("t", token.t)
                .add("refresh", "0")
                .build(),
        ).parseAs<AjaxResponse<ImagesDto>>()

        if (!images.success) {
            if (images.data.code == "locked") return emptyList()
            throw Exception(images.data.message ?: images.data.code)
        }
        return images.data.urls.mapIndexed { index, url -> Page(index, imageUrl = baseUrl + url) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/webtoon/", Fetch.DOCUMENT).asJsoup()
        .select("button.rf-chip[data-slug]")
        .associate { it.ownText() to URLDecoder.decode(it.attr("data-slug"), "UTF-8") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            ContentFilter(),
            TypeFilter(),
            StatusFilter(),
            data?.parseAs<Map<String, String>>()?.let(::GenreFilter),
        ),
    )

    companion object {
        private val READER_CONFIG_REGEX = Regex("""RAWI_IMG = (\{.*?\});""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.dd.MM")
    }
}
