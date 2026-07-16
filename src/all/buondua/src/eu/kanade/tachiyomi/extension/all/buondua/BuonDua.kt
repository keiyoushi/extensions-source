package eu.kanade.tachiyomi.extension.all.buondua

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BuonDua :
    HttpSource(),
    ConfigurableSource {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(10, 1.seconds) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .setRandomUserAgent(UserAgentType.MOBILE)

    private val preferences by getPreferencesLazy()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?start=${20 * (page - 1)}", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangasPage(response)

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/hot?start=${20 * (page - 1)}", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangasPage(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.firstInstanceOrNull<Filter.Text>()
        return when {
            query.isNotEmpty() -> {
                val urlBuilder = baseUrl.toHttpUrl().newBuilder().apply {
                    addQueryParameter("search", query)
                    addQueryParameter("start", (20 * (page - 1)).toString())
                }
                GET(urlBuilder.build(), headers)
            }
            tagFilter?.state?.isNotEmpty() == true -> GET("$baseUrl/tag/${tagFilter.state}&start=${20 * (page - 1)}", headers)
            else -> popularMangaRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangasPage(response)

    private fun parseMangasPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".blog > div").mapNotNull { element ->
            val link = element.selectFirst(".item-content .item-link") ?: return@mapNotNull null
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                title = link.text()
                setUrlWithoutDomain(link.attr("abs:href"))
            }
        }
        val hasNextPage = document.selectFirst(".pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            document.selectFirst(".article-header")?.text()
                ?.replace(titlePageRegex, "")?.trim()
                ?.let { title = it }

            val articleInfo = document.select(".article-info > strong").text()
                .replace("Buondua", "").trim()

            val password = document.select("code").text()
            val downloadAvailable = document.select(".article-links a[href]")
            val downloadLinks = downloadAvailable.joinToString("\n") { element ->
                val serviceText = element.text()
                val link = element.attr("href")
                "[$serviceText]($link)"
            }

            description = StringBuilder().apply {
                if (articleInfo.isNotBlank()) {
                    append(articleInfo)
                }
                if (downloadLinks.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append(downloadLinks)
                }
                if (password.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append(password)
                }
            }.toString().trim()

            genre = document.selectFirst(".article-tags")?.select(".tags > .tag")
                ?.joinToString { it.text().substringAfter("#") }
                ?.takeIf { it.isNotBlank() }
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val dateUploadStr = doc.selectFirst(".article-info > small")?.text()
        val dateUpload = DATE_FORMAT.tryParse(dateUploadStr)

        val basePageUrl = response.request.url.toString()

        return if (preferences.splitPages) {
            val maxPage = doc.getLastPageNum()
            (maxPage downTo 1).mapNotNull { page ->
                SChapter.create().apply {
                    setUrlWithoutDomain(
                        basePageUrl.toHttpUrlOrNull()?.newBuilder()
                            ?.setQueryParameter("page", page.toString())
                            ?.build()
                            ?.toString()
                            ?: return@mapNotNull null,
                    )
                    name = "Page $page"
                    chapter_number = page.toFloat()
                    date_upload = dateUpload
                }
            }
        } else {
            listOf(
                SChapter.create().apply {
                    chapter_number = 0F
                    setUrlWithoutDomain(basePageUrl)
                    name = "Gallery"
                    date_upload = dateUpload
                },
            )
        }
    }

    // Pages
    private val pageListSelector = ".article-fulltext img"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return if (preferences.splitPages) {
            pageListParse(document)
        } else {
            runBlocking { pageListMerge(document) }
        }
    }

    private fun pageListParse(document: Document): List<Page> = document.select(pageListSelector).mapIndexed { i, imgEl ->
        Page(i, imageUrl = imgEl.attr("abs:src"))
    }

    private suspend fun pageListMerge(document: Document): List<Page> {
        val basePageUrl = document.location()
        val maxPage = document.getLastPageNum()

        return (1..maxPage).parallelCatchingFlatMap { page ->
            val doc = when (page) {
                1 -> document
                else -> {
                    val pageUrl = basePageUrl.toHttpUrl().newBuilder()
                        .setQueryParameter("page", page.toString())
                        .build()
                        .toString()
                    client.newCall(GET(pageUrl, headers)).awaitSuccess()
                        .use { it.asJsoup() }
                }
            }
            doc.select(pageListSelector).map { imgEl ->
                imgEl.absUrl("src")
            }
        }.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    private fun Document.getLastPageNum(): Int = select("nav.pagination:first-of-type a.pagination-next").last()
        ?.attr("abs:href")
        ?.takeIf { it.startsWith("http") }
        ?.toHttpUrlOrNull()
        ?.queryParameter("page")?.toIntOrNull() ?: 1

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        object : Filter.Text("Tag ID") {},
    )

    // Settings
    private val SharedPreferences.splitPages
        get() = getBoolean(PREF_SPLIT_PAGES, DEFAULT_SPLIT_PAGES)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SPLIT_PAGES
            title = "Split into multiple pages"
            summaryOff = "Single gallery"
            summaryOn = "Multiple pages"
            setDefaultValue(DEFAULT_SPLIT_PAGES)
        }.also(screen::addPreference)
    }

    /**
     * Parallel implementation of [Iterable.flatMap], but running
     * the transformation function inside a try-catch block.
     */
    private suspend inline fun <A, B> Iterable<A>.parallelCatchingFlatMap(crossinline f: suspend (A) -> Iterable<B>): List<B> = withContext(Dispatchers.IO) {
        map {
            async {
                try {
                    f(it)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    companion object {
        private const val PREF_SPLIT_PAGES = "pref_split_pages"
        private const val DEFAULT_SPLIT_PAGES = true

        private val DATE_FORMAT = SimpleDateFormat("HH:mm dd-MM-yyyy", Locale.US)

        private val titlePageRegex by lazy { Regex(""" - \( Page \d+ / \d+ \)""") }
    }
}
