package eu.kanade.tachiyomi.extension.all.buondua

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BuonDua :
    KeiSource(),
    ConfigurableSource {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(10, 1.seconds) { it.host == baseUrlHost }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = setRandomUserAgent(UserAgentType.MOBILE)

    private val preferences by getPreferencesLazy()

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/?start=${20 * (page - 1)}"))

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/hot?start=${20 * (page - 1)}"))

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tagFilter = filters.firstInstanceOrNull<Filter.Text>()
        return when {
            query.isNotEmpty() -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addQueryParameter("search", query)
                    addQueryParameter("start", (20 * (page - 1)).toString())
                }.build()
                parseMangasPage(client.get(url))
            }
            tagFilter?.state?.isNotEmpty() == true ->
                parseMangasPage(client.get("$baseUrl/tag/${tagFilter.state}&start=${20 * (page - 1)}"))
            else -> getPopularManga(page)
        }
    }

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

    // Deeplink
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost) return null
        val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga.apply {
            this.url = manga.url
            initialized = true
        }
    }

    // Details
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
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

    // Chapters
    private fun parseChapterList(doc: Document): List<SChapter> {
        val dateUploadStr = doc.selectFirst(".article-info > small")?.text()
        val dateUpload = DATE_FORMAT.tryParse(dateUploadStr)

        val basePageUrl = doc.location()

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

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // Related
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val response = client.get(getMangaUrl(manga))
        return parseMangasPage(response).mangas
    }

    // Pages
    private val pageListSelector = ".article-fulltext img"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return if (preferences.splitPages) {
            pageListParse(document)
        } else {
            pageListMerge(document)
        }
    }

    private fun pageListParse(document: Document): List<Page> = document.select(pageListSelector).mapIndexed { i, imgEl ->
        Page(i, imageUrl = imgEl.attr("abs:src"))
    }

    private suspend fun pageListMerge(document: Document): List<Page> {
        val basePageUrl = document.location()
        val maxPage = document.getLastPageNum()

        return withContext(Dispatchers.IO) {
            (1..maxPage).map { page ->
                async {
                    val doc = when (page) {
                        1 -> document
                        else -> {
                            val pageUrl = basePageUrl.toHttpUrl().newBuilder()
                                .setQueryParameter("page", page.toString())
                                .build()
                            client.get(pageUrl).asJsoup()
                        }
                    }
                    doc.select(pageListSelector).map { imgEl ->
                        imgEl.absUrl("src")
                    }
                }
            }.awaitAll().flatten()
        }.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    private fun Document.getLastPageNum(): Int = select("nav.pagination:first-of-type a.pagination-next").last()
        ?.attr("abs:href")
        ?.takeIf { it.startsWith("http") }
        ?.toHttpUrlOrNull()
        ?.queryParameter("page")?.toIntOrNull() ?: 1

    // Filters
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
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

    companion object {
        private const val PREF_SPLIT_PAGES = "pref_split_pages"
        private const val DEFAULT_SPLIT_PAGES = true

        private val DATE_FORMAT = SimpleDateFormat("HH:mm dd-MM-yyyy", Locale.US)

        private val titlePageRegex by lazy { Regex(""" - \( Page \d+ / \d+ \)""") }
    }
}
