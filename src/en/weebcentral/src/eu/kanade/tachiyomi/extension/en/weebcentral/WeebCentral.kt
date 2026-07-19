package eu.kanade.tachiyomi.extension.en.weebcentral

import eu.kanade.tachiyomi.network.GET
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class WeebCentral : KeiSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 2.seconds) { it.host == baseUrlHost }
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", defaultFilterList(SortFilter("Popularity")))

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", defaultFilterList(SortFilter("Latest Updates")))

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val filterList = filters.ifEmpty { getFilterList() }
        val url = "$baseUrl/search/data".toHttpUrl().newBuilder().apply {
            addQueryParameter("text", query.replace(excludedSearchCharacters, " ").trim())
            filterList.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
            addQueryParameter("limit", FETCH_LIMIT.toString())
            addQueryParameter("offset", ((page - 1) * FETCH_LIMIT).toString())
            addQueryParameter("display_mode", "Full Display")
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select("article > section > a").map { element ->
            SManga.create().apply {
                thumbnail_url = element.sourceImg()
                title = element.selectFirst("div:not([class]):last-child")!!.text()
                setUrlWithoutDomain(element.attr("abs:href"))
            }
        }
        val hasNextPage = document.selectFirst("button") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost || url.pathSegments.size < 3) {
            return null
        }

        val manga = SManga.create().apply {
            this.url = "/series/${url.pathSegments[1]}/${url.pathSegments[2]}"
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
            }
    }

    // =============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = defaultFilterList(SortFilter())

    // =========================== Manga Updates ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (manga, chapters) = coroutineScope {
            val mangaD = async { if (fetchDetails) getMangaDetails(manga) else manga }
            val chaptersD = async { if (fetchChapters) getChapterList(manga) else chapters }
            mangaD.await() to chaptersD.await()
        }

        return SMangaUpdate(manga, chapters)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val document = client.get(baseUrl + manga.url).asJsoup()

        return SManga.create().apply {
            with(document.select("section[x-data] > section")[0]) {
                thumbnail_url = sourceImg()
                author = select("ul > li:has(strong:contains(Author)) > span > a").joinToString { it.text() }
                genre = select("ul > li:has(strong:contains(Tag),strong:contains(Type)) a").joinToString { it.text() }
                status = selectFirst("ul > li:has(strong:contains(Status)) > a").parseStatus()
            }

            with(document.select("section[x-data] > section")[1]) {
                title = selectFirst("h1")!!.text()

                description = buildString {
                    selectFirst("li:has(strong:contains(Description)) > p")?.text()?.let {
                        append(it.replace("NOTE: ", "\n\nNOTE: "))
                    }

                    val relatedSeries = select("li:has(strong:contains(Related Series)) li")
                    if (relatedSeries.isNotEmpty()) {
                        append("\n\nRelated Series(s):")
                        relatedSeries.forEach { series ->
                            append("\n• ${series.text()}")
                        }
                    }

                    val alternateTitles = select("li:has(strong:contains(Associated Name)) li")
                    if (alternateTitles.isNotEmpty()) {
                        append("\n\nAssociated Name(s):")
                        alternateTitles.forEach { append("\n• ${it.text()}") }
                    }
                }
            }

            setUrlWithoutDomain(document.location())
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "complete" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val url = (baseUrl + manga.url).toHttpUrl().newBuilder().apply {
            removePathSegment(2)
            addPathSegment("full-chapter-list")
        }.build()

        val document = client.get(url).asJsoup()
        return document.select("div[x-data] > a").map { element ->
            SChapter.create().apply {
                name = element.selectFirst("span.flex > span")!!.text()
                setUrlWithoutDomain(element.attr("abs:href"))
                element.selectFirst("time[datetime]")?.also {
                    date_upload = Instant.parseOrNull(it.attr("datetime"))?.toEpochMilliseconds() ?: 0L
                }
                element.selectFirst("svg")?.attr("stroke")?.also { stroke ->
                    scanlator = when (stroke) {
                        "#d8b4fe" -> "Official"
                        "#4C4D54" -> "Unknown"
                        else -> null
                    }
                }
            }
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val newUrl = (baseUrl + chapter.url)
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("images")
            ?.addQueryParameter("is_prev", "False")
            ?.addQueryParameter("reading_style", "long_strip")
            ?.build()
            ?.toString()
            ?: (baseUrl + chapter.url)

        val document = client.get(newUrl).asJsoup()
        return document.select("section[x-data~=scroll] > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    private fun Element.sourceImg(): String? = selectFirst("source")?.attr("srcset")?.replace("small", "normal")
        ?: selectFirst("img")?.attr("abs:src")

    private fun defaultFilterList(sortFilter: SortFilter): FilterList = FilterList(
        sortFilter,
        SortOrderFilter(),
        OfficialTranslationFilter(),
        AnimeAdaptationFilter(),
        AdultContentFilter(),
        AuthorFilter(),
        StatusFilter(),
        TypeFilter(),
        TagFilter(),
    )

    companion object {
        // The related "&limit=" query parameter of the api is currently non functional
        // and always returns 32 entries per request
        const val FETCH_LIMIT = 32

        private val excludedSearchCharacters = "[!#:(),-]".toRegex()
    }
}
