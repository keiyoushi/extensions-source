package eu.kanade.tachiyomi.extension.zh.bilimanga

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BiliManga :
    KeiSource(),
    ConfigurableSource {

    private val pref by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context, pref).forEach(screen::addPreference)
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ChapterInterceptor())
        val split = pref.getString(PREF_RATE_LIMIT, "10/10")!!.split("/")
        rateLimit(split[0].toInt(), split[1].toInt().seconds)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        add("Accept-Language", "zh")
        add("Accept", "*/*")
    }

    // Customize

    private val SManga.id get() = MANGA_ID_REGEX.find(url)!!.groups[1]!!.value
    private fun Element.formatText(c: String) = this.wholeText().replace(NEWLINE_REGEX, c).trim()
    private fun Elements.mapToChapter(date: Long, volume: String? = null) = mapIndexed { i, element ->
        val url = element.absUrl("href").takeUnless("javascript:cid(1)"::equals)
        SChapter.create().apply {
            name = element.text().toHalfWidthDigits()
            date_upload = date
            scanlator = volume
            setUrlWithoutDomain(url ?: getChapterUrlByContext(i, this@mapToChapter))
        }
    }

    private fun String.toHalfWidthDigits(): String = this.map { if (it in '０'..'９') it - 65248 else it }.joinToString("")

    private fun buildDescription(doc: Document): String {
        val configs = pref.getStringSet(PREF_DESCRIPTION, DEFAULT_SET)!!
        val desc = StringBuilder(doc.selectFirst("#bookSummary > content")!!.formatText("\n\n\n"))
        for (item in configs) {
            when (item) {
                "A" -> {
                    desc.insert(
                        0,
                        doc.selectFirst(".notice")?.let { "> ${it.formatText("\n")}\n\n" } ?: "",
                    )
                }

                "B" -> {
                    desc.append(
                        doc.selectFirst(".backupname")?.let { "\n\n\n***别名**：${it.text()}* " } ?: "",
                    )
                }

                "C" -> {
                    desc.append(
                        doc.select(".book-detail-btn .btn-group-cell > a").find { it.text() == "輕小說" }
                            ?.attr("href")?.substringAfter('?')
                            ?.let { "\n\n\n***[跳轉至「哔哩轻小说」上的同名小說](https://www.bilinovel.com/novel/$it.html)*** " }
                            ?: "",
                    )
                }
            }
        }
        return desc.toString()
    }

    companion object {
        val NEWLINE_REGEX = Regex("(?:\n\r\n)+")
        val META_REGEX = Regex("收藏|推薦|連載中|已完結")
        val DATE_REGEX = Regex("\\d{4}-\\d{1,2}-\\d{1,2}")
        val PAGE_REGEX = Regex("第(\\d+)/(\\d+)页")
        val MANGA_ID_REGEX = Regex("/detail/(\\d+)\\.html")
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
    }

    private fun getChapterUrlByContext(i: Int, els: Elements) = when (i) {
        0 -> "${els[1].attr("href")}#prev"
        else -> "${els[i - 1].attr("href")}#next"
    }

    private fun mangaPageParse(response: Response) = response.asJsoup().let { doc ->
        val mangas = doc.select(".book-layout").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                val img = it.selectFirst("img")!!
                thumbnail_url = img.absUrl("data-src")
                title = img.attr("alt")
            }
        }
        val hasNextPage = with(doc.location()) {
            when {
                contains("filter") -> {
                    val total = doc.selectFirst("#pagelink > .last")?.text()?.toInt()
                    val cur = doc.selectFirst("#pagelink > strong")?.text()?.toInt()
                    total != null && cur != null && cur < total
                }

                contains("search") -> {
                    val find = doc.selectFirst("#pagelink > span")?.text()?.let(PAGE_REGEX::find)
                    find != null && find.groups[1]!!.value.toInt() < find.groups[2]!!.value.toInt()
                }

                else -> mangas.size == 50
            }
        }
        MangasPage(mangas, hasNextPage)
    }

    private fun Response.parseManga() = SManga.create().apply {
        val doc = asJsoup()
        doc.selectFirst(".aui-ver-form")?.let { throw Exception(it.text()) }
        val meta = doc.select(".book-meta em").map(Element::text)
        val (main, extra) = meta.partition(META_REGEX::containsMatchIn)
        setUrlWithoutDomain(doc.location())
        title = doc.selectFirst(".book-title")!!.text()
        thumbnail_url = doc.selectFirst(".book-cover")!!.attr("src")
        description = buildDescription(doc)
        artist = doc.selectFirst(".authorname")?.text()
        author = doc.selectFirst(".illname")?.text() ?: artist
        status = when (main.lastOrNull()) {
            "連載中" -> SManga.ONGOING
            "已完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = (doc.select(".tag-small").map(Element::text) + extra).joinToString()
        initialized = true
    }

    // Popular Page

    override suspend fun getPopularManga(page: Int): MangasPage {
        val suffix = pref.getString(PREF_POPULAR_MANGA_DISPLAY, "/top/weekvisit/%d.html")!!
        return mangaPageParse(client.get(baseUrl + String.format(suffix, page)))
    }

    // Latest Page

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaPageParse(client.get("$baseUrl/top/lastupdate/$page.html"))

    // Search Page

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!MANGA_ID_REGEX.matches(url.encodedPath)) return null
        return client.get(url).parseManga()
    }

    // /${Sort}_${Theme}_${Status}_${Anime}_${Region}_${Type}_${Time}_${Novel}_${page}_0_${Year}_${Award}.html
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegment("search").addPathSegment("${query}_$page.html")
        } else {
            url.addPathSegment("filter")
                .addPathSegment("${filters[5]}_${filters[1]}_${filters[9]}_${filters[6]}_${filters[3]}_${filters[2]}_${filters[10]}_${filters[7]}_${page}_0_${filters[4]}_${filters[8]}.html")
        }
        val response = client.get(url.build())
        if (response.request.url.pathSegments.contains("detail")) {
            return MangasPage(listOf(response.parseManga()), false)
        }
        return mangaPageParse(response)
    }

    // Manga Detail Page

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = supervisorScope {
        val asyncManga = if (fetchDetails) {
            async { client.get(baseUrl + manga.url).parseManga() }
        } else {
            CompletableDeferred(manga)
        }

        val asyncChapters = if (fetchChapters) {
            async {
                val doc = client.get("$baseUrl/read/${manga.id}/catalog").asJsoup()
                val info = doc.selectFirst(".chapter-sub-title")!!.text()
                val title = doc.selectFirst(".book-title")!!.text()
                val date = DATE_FORMAT.tryParse(DATE_REGEX.find(info)?.value)
                doc.select(".catalog-volume").takeIf(Elements::isNotEmpty)?.flatMap {
                    val bar = it.selectFirst(".chapter-bar")!!.text().substring(title.length + 1)
                    val volume = if (bar.first().isDigit()) "Vol.$bar" else bar.toHalfWidthDigits()
                    it.select(".chapter-li-a").mapToChapter(date, volume)
                }?.reversed() ?: doc.select(".chapter-li-a").mapToChapter(date).reversed()
            }
        } else {
            CompletableDeferred(chapters)
        }

        SMangaUpdate(asyncManga.await(), asyncChapters.await())
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return doc.select("#book-friend-list-container").last()?.select(".module-slide-a")?.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".module-slide-caption")!!.text()
                thumbnail_url = it.selectFirst(".module-slide-img")!!.attr("data-src")
            }
        } ?: emptyList()
    }

    // Manga View Page

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url)
        return response.asJsoup().let { doc ->
            val images = doc.select(".imagecontent")
            check(images.isNotEmpty()) {
                doc.selectFirst("#acontentz")?.let {
                    if ("電腦端" in it.text()) "不支持電腦端查看，請在高級設置中更換移動端UA標識" else "漫畫可能已下架或需要足夠的權限"
                } ?: "章节鏈接错误"
            }
            images.mapIndexed { i, img -> Page(i, imageUrl = img.attr("data-src")) }
        }
    }
}
