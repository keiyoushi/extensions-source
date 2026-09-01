package eu.kanade.tachiyomi.extension.en.todaymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class TodayManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/category/most-popular".addPage(page)).asJsoup()
        val mangaList = document.select("section > main > div.series-info").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(".pagination > ul > li.active + li:has(a)") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[href]")!!.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
        title = element.selectFirst(".series-name")!!.text()
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/category/recent".addPage(page)).asJsoup()
        val mangaList = document.select("ul.series > li").map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst(".pagination > ul > li.active + li:has(a)") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".series-name")!!.text()
        with(element.selectFirst("a[title][href]")!!) {
            setUrlWithoutDomain(attr("abs:href"))
        }
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categoryFilter = filters.firstInstance<CategoryFilter>()
        val genreFilter = filters.firstInstance<GenreFilter>()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            when {
                categoryFilter.state != 0 -> {
                    addPathSegment("category")
                    addPathSegments(categoryFilter.toUriPart())
                }
                genreFilter.state != 0 -> {
                    addPathSegment("genre")
                    addPathSegment(genreFilter.toUriPart())
                }
                query.isNotBlank() -> {
                    addPathSegment("search")
                    addQueryParameter("q", query)
                }
                else -> {
                    addPathSegments("category/most-popular")
                }
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()
        val mangaList = document.select("section div.serie")
            .map { popularMangaFromElement(it) }
            .ifEmpty {
                document.select("ul.series > li")
                    .map { latestUpdatesFromElement(it) }
            }

        val hasNextPage = document.selectFirst(".pagination > ul > li.active + li:has(a)") != null
        return MangasPage(mangaList, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "book") {
            return null
        }

        val mangaUrl = "/book/${url.pathSegments[1]}"
        val manga = SManga.create().apply {
            this.url = mangaUrl
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                this.url = mangaUrl
                initialized = true
            }
    }

    // =============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Ignored when using text search"),
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        CategoryFilter(),
        GenreFilter(),
    )

    // =========================== Manga Updates ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        val manga = SManga.create().apply {
            with(document.selectFirst(".series-info")!!) {
                title = selectFirst("h1")!!.text()
                thumbnail_url = selectFirst("img")!!.imgAttr()
                genre = select("*[itemprop=genre] > a").joinToString { it.text() }
                author = select("span[itemprop=author] > span").joinToString { it.text() }
                status = selectFirst("*:containsOwn(Status) + *").parseStatus()
            }

            description = buildString {
                val summary = document.selectFirst(".series-summary")!!
                summary.childNodes().forEach { node ->
                    if (node is TextNode) append(node.text())
                    if (node.nodeName() == "br") appendLine()
                }
                summary.selectFirst("div[style]")?.also {
                    append("\n\n")
                    append(it.text())
                }
            }.trim()
        }

        val chapters = document.select("#chapList > li").map { element ->
            SChapter.create().apply {
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                name = element.selectFirst("strong")?.text() ?: link.text()

                val dateText = element.selectFirst("span.muted")?.text()
                date_upload = when {
                    dateText == null -> 0L
                    dateText.contains("ago") -> dateText.parseRelativeDate()
                    else -> dateFormat.tryParseDate(dateText)
                }
            }
        }

        return SMangaUpdate(manga, chapters)
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "complete" -> SManga.COMPLETED
        "on going" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val relativeDate = this.split(" ").firstOrNull()
            ?.replace("one", "1")
            ?.replace("a", "1")
            ?.toIntOrNull()
            ?: return 0L

        when {
            "second" in this -> now.add(Calendar.SECOND, -relativeDate)
            "minute" in this -> now.add(Calendar.MINUTE, -relativeDate)
            "hour" in this -> now.add(Calendar.HOUR, -relativeDate)
            "day" in this -> now.add(Calendar.DAY_OF_YEAR, -relativeDate)
            "week" in this -> now.add(Calendar.WEEK_OF_YEAR, -relativeDate)
            "month" in this -> now.add(Calendar.MONTH, -relativeDate)
            "year" in this -> now.add(Calendar.YEAR, -relativeDate)
        }
        return now.timeInMillis
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        return document.select(".chapter-content > img[data-index]").map { img ->
            Page(img.attr("data-index").toInt(), imageUrl = img.imgAttr())
        }.sortedBy { it.index }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    private fun String.addPage(page: Int): HttpUrl = toHttpUrl().newBuilder().apply {
        if (page > 1) addQueryParameter("page", page.toString())
    }.build()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
