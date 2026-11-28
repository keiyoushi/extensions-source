package eu.kanade.tachiyomi.extension.ja.spoilerplus

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.xor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.net.URLDecoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Source
abstract class SpoilerPlus : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("ranking")
            .apply { if (page > 1) addPathSegment(page.toString()) }
            .addPathSegment("")
            .build()
        return getMangaList(url)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listUrl(page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val term = filters.filterIsInstance<TermFilter>().firstNotNullOfOrNull { filter -> filter.selected?.let { filter.path to it } }
        val url = when {
            query.isNotEmpty() -> baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .apply { if (page > 1) addQueryParameter("page", page.toString()) }
                .build()
            else -> listUrl(page, term)
        }
        return getMangaList(url)
    }

    // Series pages decode their path twice, so a literal % or + needs one more round of escaping
    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(manga.url.replace("%", "%25").replace("+", "%2B"))
        .addPathSegment("")
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(chapter.url)
        .addPathSegment("")
        .build()
        .toString()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.first()
        if (url.host != baseUrl.toHttpUrl().host || !slug.endsWith("-raw-free")) return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
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

    private fun parseDetails(document: Document, slug: String) = SManga.create().apply {
        val info = document.selectFirst("article#item-detail")!!
        url = slug
        title = info.selectFirst("h1.title-detail")!!.text().removeSuffix(TITLE_SUFFIX)
        thumbnail_url = info.selectFirst("div.col-image img")?.attrOrNull("abs:src")
        author = info.selectFirst("li.author p.col-xs-8")?.text()?.takeIf { it != "更新中" }
        status = when (info.selectFirst("li:has(i.fa-rss) p.col-xs-8")?.text()) {
            "連載中" -> SManga.ONGOING
            "完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = info.select("li.kind p.col-xs-8 a").map { it.text() }.distinct().joinToString()

        // The synopsis opens with a bold "<romanized title> Raw free" line
        val paragraphs = info.select("div.detail-content > p:not(.shortened)")
        val romanized = paragraphs.firstOrNull()?.takeIf { it.text() == it.selectFirst("strong")?.text() }
        val altTitles = listOfNotNull(
            romanized?.text()?.let { ROMANIZED_SUFFIX.replace(it, "") },
            info.selectFirst("li:has(i.fa-link) p.col-xs-8")?.text(),
        ).filter { it != title }

        description = MarkdownDescription.Builder().apply {
            paragraphs.filter { it !== romanized }.forEach { description(it) }
            altTitles(altTitles, label = "別の名前")
        }.build()
    }

    private fun parseChapters(document: Document) = document.select("div.list-chapter li.row:not(.heading)").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst("a")!!
            url = link.absUrl("href").toHttpUrl().pathSegments.filter { it.isNotEmpty() }.joinToString("/")
            name = link.text()
            date_upload = parseDate(element.selectFirst("div.col-xs-4")?.text())
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val script = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup().selectFirst("script:containsData(window.MangaId)")!!.data()
        val body = ChapterImagesRequest(
            mangaId = MANGA_ID_REGEX.find(script)!!.groupValues[1].toInt(),
            chapterNumber = CHAPTER_NUMBER_REGEX.find(script)!!.groupValues[1].parseAs<JsonPrimitive>(),
        ).toJsonRequestBody()
        val images = client.post(baseUrl.toHttpUrl().resolve("/api/v1/get/c")!!, Fetch.XHR.from(chapterUrl), body).parseAs<ChapterImagesDto>()

        // Hex strings XORed with the XOR of the host's char codes
        val key = baseUrl.toHttpUrl().host.fold(0) { acc, char -> acc xor char.code }
        fun String.decode() = decodeHex().xor(key).decodeToString()
        val imageBase = images.imageBase.decode().toHttpUrl()
        // Older chapters come without a cell order and are not shuffled
        val cellOrder = images.cellOrder.takeIf { it.isNotEmpty() }?.decode()
        return images.paths.mapIndexed { index, path ->
            Page(index, imageUrl = imageBase.resolve(path)!!.newBuilder().fragment(cellOrder).build().toString())
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val (genres, tags) = listOf("sitemap-genre.xml", "sitemap-tag.xml")
            .map { file -> async { fetchSitemapTerms(file) } }
            .map { it.await() }
        FilterData(genres, tags).toJsonElement()
    }

    // Entries as (name, path segment); the sitemap form-encodes names, spaces as +
    private suspend fun fetchSitemapTerms(file: String): List<Pair<String, String>> = client.get(baseUrl.toHttpUrl().resolve("/$file")!!, Fetch.DOCUMENT)
        .asJsoup(Parser.xmlParser())
        .select("loc")
        .map { loc ->
            val segment = loc.text().toHttpUrl().encodedPathSegments[1]
            URLDecoder.decode(segment, "UTF-8") to segment
        }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        return FilterList(
            Filter.Header("テキスト検索ではジャンルとタグは無視されます"),
            TermFilter("ジャンル", "genre", filterData.genres),
            TermFilter("タグ", "tag", filterData.tags),
        )
    }

    private fun listUrl(page: Int, term: Pair<String, String>? = null): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        // Encoded once more, as listing paths are decoded twice too
        term?.let { (path, segment) -> addPathSegment(path).addPathSegment(segment) }
        if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        addPathSegment("")
    }.build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.items article.item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("h3 a")!!
                this.url = link.absUrl("href").toHttpUrl().pathSegments.first()
                title = link.text().removeSuffix(TITLE_SUFFIX)
                thumbnail_url = element.selectFirst("div.image img")?.attrOrNull("abs:data-src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    private fun parseDate(text: String?): Long {
        text ?: return 0L
        val relative = RELATIVE_DATE_REGEX.find(text)
            ?: return DATE_FORMAT.tryParseDate(text, ZONE)
        val (amount, unit) = relative.destructured
        return ZonedDateTime.now(ZONE).minus(amount.toLong(), DATE_UNITS.getValue(unit)).toInstant().toEpochMilli()
    }

    companion object {
        private const val TITLE_SUFFIX = " Raw Free"

        private val MANGA_ID_REGEX = Regex("""window\.MangaId\s*=\s*(\d+)""")
        private val CHAPTER_NUMBER_REGEX = Regex("""window\.CNumber\s*=\s*([\d.]+)""")
        private val ROMANIZED_SUFFIX = Regex("""\s+Raw(\s+free)?$""", RegexOption.IGNORE_CASE)
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(秒|分|時間|日|週間|ヶ月|年)前""")

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.ROOT)
        private val ZONE = ZoneId.of("Asia/Tokyo")

        private val DATE_UNITS = mapOf(
            "秒" to ChronoUnit.SECONDS,
            "分" to ChronoUnit.MINUTES,
            "時間" to ChronoUnit.HOURS,
            "日" to ChronoUnit.DAYS,
            "週間" to ChronoUnit.WEEKS,
            "ヶ月" to ChronoUnit.MONTHS,
            "年" to ChronoUnit.YEARS,
        )
    }
}
