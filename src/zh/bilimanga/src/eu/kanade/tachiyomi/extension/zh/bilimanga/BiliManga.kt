package eu.kanade.tachiyomi.extension.zh.bilimanga

import android.graphics.BitmapFactory
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.escapeMarkdown
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.selectLast
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BiliManga :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context).forEach(screen::addPreference)
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addNetworkInterceptor(ChapterInterceptor())
        addCookie("night" to "1")
        rateLimit(10, 10.seconds) { it.host == baseUrl.toHttpUrl().host && it.pathSegments.first() != "files" }
    }

    override fun Headers.Builder.configureHeaders() = apply {
        add("Accept-Language", "zh")
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val path = preferences.getString(PREF_POPULAR_MANGA_DISPLAY, "/top/weekvisit/%d.html")!!.format(page)
        return parseMangasPage(client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/top/lastupdate/$page.html", Fetch.DOCUMENT).asJsoup())

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !MANGA_ID_REGEX.matches(url.encodedPath)) return null
        return parseManga(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val document = if (query.isNotBlank()) {
            searchDocument(baseUrl.toHttpUrl().newBuilder().addPathSegment("search").addPathSegment("${query}_$page.html").build())
        } else {
            val path = listOf(
                filters.firstInstance<SortFilter>().value,
                filters.firstInstance<ThemeFilter>().value,
                filters.firstInstance<StatusFilter>().value,
                filters.firstInstance<AnimeFilter>().value,
                filters.firstInstance<RegionFilter>().value,
                filters.firstInstance<TypeFilter>().value,
                filters.firstInstance<TimeFilter>().value,
                filters.firstInstance<NovelFilter>().value,
                page.toString(),
                "0",
                filters.firstInstance<YearFilter>().value,
                filters.firstInstance<AwardFilter>().value,
            ).joinToString("_")
            client.get(baseUrl.toHttpUrl().newBuilder().addPathSegment("filter").addPathSegment("$path.html").build(), Fetch.DOCUMENT).asJsoup()
        }
        if (MANGA_ID_REGEX.containsMatchIn(document.location())) {
            return MangasPage(listOf(parseManga(document)), false)
        }
        return parseMangasPage(document)
    }

    private suspend fun searchDocument(url: HttpUrl): Document {
        var document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val solveCaptcha = preferences.getBoolean(PREF_SOLVE_CAPTCHA, false)
        if (solveCaptcha) {
            repeat(3) {
                val form = document.selectFirst(CAPTCHA_FORM) ?: return document
                val image = form.selectFirst("img")!!.absUrl("src")
                val bitmap = client.get(image, Fetch.IMAGE.from(document.location())).use { BitmapFactory.decodeStream(it.body.byteStream()) }
                    ?: throw Exception(CAPTCHA_UNSOLVED)
                val code = solveSearchCaptcha(bitmap).also { bitmap.recycle() }
                val body = FormBody.Builder().apply {
                    form.select("input[type=hidden]").forEach { add(it.attr("name"), it.attr("value")) }
                    add("search_code", code)
                }.build()
                document = client.post(form.absUrl("action"), Fetch.DOCUMENT.from(document.location()), body).asJsoup()
            }
        }
        if (document.selectFirst(CAPTCHA_FORM) != null) {
            if (solveCaptcha) throw Exception(CAPTCHA_UNSOLVED)
            throw Exception("請在 WebView 中搜尋一次並輸入驗證碼，或在擴充功能設定中開啟「自動輸入搜尋驗證碼」")
        }
        return document
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = coroutineScope {
        val asyncManga = if (fetchDetails) {
            async { parseManga(client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()) }
        } else {
            CompletableDeferred(manga)
        }

        val asyncChapters = if (fetchChapters) {
            async { parseChapters(client.get("$baseUrl/read/${manga.id}/catalog", Fetch.DOCUMENT).asJsoup()) }
        } else {
            CompletableDeferred(chapters)
        }

        SMangaUpdate(asyncManga.await(), asyncChapters.await())
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return document.selectLast("#book-friend-list-container")?.select(".module-slide-a")?.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".module-slide-caption")!!.text()
                thumbnail_url = it.selectFirst(".module-slide-img")?.absUrl("data-src")
            }
        }.orEmpty()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val images = document.select(".imagecontent")
        check(images.isNotEmpty()) {
            document.selectFirst("#acontentz")?.let {
                if ("電腦端" in it.text()) "不支持電腦端查看，請在高級設置中更換移動端UA標識" else "漫畫可能已下架或需要足夠的權限"
            } ?: "章节鏈接错误"
        }
        return images.mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("data-src")) }
    }

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    private val SManga.id get() = MANGA_ID_REGEX.find(url)!!.groupValues[1]

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".book-layout").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                val image = it.selectFirst("img")!!
                thumbnail_url = image.absUrl("data-src")
                title = image.attr("alt")
            }
        }
        val hasNextPage = when (document.location().toHttpUrl().pathSegments.first()) {
            "filter" -> {
                val total = document.selectFirst("#pagelink > .last")?.text()?.toInt()
                val current = document.selectFirst("#pagelink > strong")?.text()?.toInt()
                total != null && current != null && current < total
            }

            "search" -> {
                val match = document.selectFirst("#pagelink > span")?.text()?.let(PAGE_REGEX::find)
                match != null && match.groupValues[1].toInt() < match.groupValues[2].toInt()
            }

            else -> mangas.size == 50
        }
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseManga(document: Document) = SManga.create().apply {
        document.selectFirst(".aui-ver-form")?.let { throw Exception(it.text()) }
        val (main, extra) = document.select(".book-meta em").map(Element::text).partition(META_REGEX::containsMatchIn)
        setUrlWithoutDomain(document.location())
        title = document.selectFirst(".book-title")!!.text()
        thumbnail_url = document.selectFirst(".book-cover")?.absUrl("src")
        description = buildDescription(document)
        artist = document.selectFirst(".authorname")?.text()
        author = document.selectFirst(".illname")?.text() ?: artist
        status = when (main.lastOrNull()) {
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = (document.select(".tag-small").map(Element::text) + extra).joinToString()
        initialized = true
    }

    private fun buildDescription(document: Document): String? {
        val sections = preferences.getStringSet(PREF_DESCRIPTION, DEFAULT_SET)!!
        return MarkdownDescription.Builder().apply {
            if ("A" in sections) {
                markdown(document.selectFirst(".notice")?.formatText("\n")?.escapeMarkdown()?.let { "> $it" })
            }
            description(document.selectFirst("#bookSummary > content")?.formatText("\n\n"))
            if ("B" in sections) {
                field("别名", document.selectFirst(".backupname")?.text())
            }
            if ("C" in sections) {
                val novel = document.select(".book-detail-btn .btn-group-cell > a").find { it.text() == "輕小說" }
                    ?.absUrl("href")?.toHttpUrl()?.query
                if (novel != null) links(listOf("跳轉至「哔哩轻小说」上的同名小說" to "https://www.bilinovel.com/novel/$novel.html"), "輕小說")
            }
        }.build()
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val title = document.selectFirst(".book-title")!!.text()
        val volumes = document.select(".catalog-volume")
        val chapters = if (volumes.isEmpty()) {
            document.select(".chapter-li-a").toChapters()
        } else {
            volumes.flatMap {
                val bar = it.selectFirst(".chapter-bar")!!.text().substring(title.length + 1)
                val volume = if (bar.first().isDigit()) "Vol.$bar" else bar.toHalfWidthDigits()
                it.select(".chapter-li-a").toChapters(volume)
            }
        }.reversed()
        val updated = DATE_REGEX.find(document.selectFirst(".chapter-sub-title")!!.text())?.value
        chapters.firstOrNull()?.date_upload = DATE_FORMAT.tryParseDate(updated)
        return chapters
    }

    private fun Elements.toChapters(volume: String? = null) = mapIndexed { index, element ->
        SChapter.create().apply {
            name = element.text().toHalfWidthDigits()
            scanlator = volume
            val url = element.absUrl("href").takeUnless("javascript:cid(1)"::equals)
            setUrlWithoutDomain(url ?: neighbourUrl(index, this@toChapters))
        }
    }

    // Hidden chapters are read from the next page of their neighbour
    private fun neighbourUrl(index: Int, elements: Elements) = when (index) {
        0 -> "${elements[1].attr("href")}#prev"
        else -> "${elements[index - 1].attr("href")}#next"
    }

    private fun Element.formatText(separator: String) = wholeText().replace(NEWLINE_REGEX, separator).trim()

    private fun String.toHalfWidthDigits() = map { if (it in '０'..'９') it - 65248 else it }.joinToString("")

    companion object {
        private val NEWLINE_REGEX = Regex("(?:\n\r\n)+")
        private val META_REGEX = Regex("收藏|推薦|連載中|已完結")
        private val DATE_REGEX = Regex("""\d{4}-\d{1,2}-\d{1,2}""")
        private val PAGE_REGEX = Regex("""第(\d+)/(\d+)页""")
        private val MANGA_ID_REGEX = Regex("""/detail/(\d+)\.html""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d").withZone(ZoneId.of("Asia/Shanghai"))
        private const val CAPTCHA_FORM = "form:has(img[src*=checkcode.php])"
        private const val CAPTCHA_UNSOLVED = "無法自動識別搜尋驗證碼，請在 WebView 中搜尋一次並輸入驗證碼"
    }
}
