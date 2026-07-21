package eu.kanade.tachiyomi.extension.vi.hentaivnx

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HentaiVNx : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(SortByList(1)),
    )

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/?page=$page").asJsoup())

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegments("tim-truyen")
                addQueryParameter("keyword", query)
            } else {
                addPathSegment("tim-truyen-nang-cao")
                filters.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> filter.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_INCLUDE -> addQueryParameter("genres", genre.genre)
                                Filter.TriState.STATE_EXCLUDE -> addQueryParameter("notgenres", genre.genre)
                            }
                        }

                        is ChapterCountList -> addQueryParameter("minchapter", filter.values[filter.state].genre)

                        is SortByList -> addQueryParameter("sort", filter.values[filter.state].genre)

                        is TextField -> addQueryParameter("contain", filter.state)

                        else -> {}
                    }
                }
            }
            addQueryParameter("page", page.toString())
        }.build()

        return mangaListParse(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-hentai") {
            return null
        }

        val mangaUrl = if (url.pathSegments.size == 2) {
            url
        } else {
            client.get(url).asJsoup()
                .selectFirst("a.itemcrumb[href*=/truyen-hentai/]")
                ?.absUrl("href")
                ?.toHttpUrlOrNull()
                ?: return null
        }

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaUrl.encodedPath) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true).manga
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-truyen-nang-cao").asJsoup()
        .select(".genre-item")
        .mapNotNull { element ->
            val name = element.attr("title").ifEmpty { element.text() }
                .takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val id = element.selectFirst(".icon-checkbox[data-id]")
                ?.attr("data-id")
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            GenreOption(name, id)
        }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // ============================== Lists =================================

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select(".items .item").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("h3 a")
                    ?: element.selectFirst("a.jtip")
                    ?: element.selectFirst(".image a")!!
                setUrlWithoutDomain(linkElement.absUrl("href"))
                title = linkElement.attr("title").ifEmpty { linkElement.text() }
                thumbnail_url = element.selectFirst("img")?.let(::imageElement)
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination li:last-child:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h1.title-detail")!!.text()
        author = document.selectFirst("li.author .col-xs-8")?.text()
        description = document.select(".detail-content").joinToString { it.wholeText().trim() }
        genre = document.select("li.kind .col-xs-8 a").joinToString { it.text() }
        thumbnail_url = document.selectFirst(".detail-info .col-image img")?.let(::imageElement)

        val statusText = document.selectFirst("li.status .col-xs-8")?.text()?.lowercase()
        status = when {
            statusText == null -> SManga.UNKNOWN
            "đang tiến hành" in statusText -> SManga.ONGOING
            "hoàn thành" in statusText -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ===============================

    private fun chapterListParse(document: Document): List<SChapter> = document.select("#nt_listchapter.list-chapter ul li.row").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst("div.chapter a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = element.selectFirst("div.col-xs-4")?.text().toDate()
        }
    }

    private fun String?.toDate(): Long {
        this ?: return 0L
        if (!contains("trước", ignoreCase = true)) return 0L

        for ((pattern, toDuration) in relativeDatePatterns) {
            pattern.find(this)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
                return (Clock.System.now() - toDuration(number)).toEpochMilliseconds()
            }
        }
        return 0L
    }

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val images = document.select(".reading-detail img, .page-chapter img")
            .ifEmpty { document.select(".chapter-content img") }

        return images.mapIndexed { index, element ->
            Page(index, imageUrl = imageElement(element))
        }
    }

    private fun imageElement(element: Element): String? {
        val rawUrl = when {
            element.hasAttr("data-original") -> element.attr("abs:data-original")
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            else -> element.attr("abs:src")
        }
        return normalizeImageUrl(rawUrl)
    }

    private fun normalizeImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val parsedUrl = url.toHttpUrlOrNull() ?: return url

        return if (
            parsedUrl.host == "external-content.duckduckgo.com" &&
            parsedUrl.encodedPath == "/iu/"
        ) {
            parsedUrl.queryParameter("u")?.takeIf { it.isNotBlank() } ?: url
        } else {
            url
        }
    }

    private val relativeDatePatterns: List<Pair<Regex, (Int) -> Duration>> = listOf(
        Regex("""(\d+)\s*giờ""", RegexOption.IGNORE_CASE) to { it.hours },
        Regex("""(\d+)\s*ngày""", RegexOption.IGNORE_CASE) to { it.days },
        Regex("""(\d+)\s*tuần""", RegexOption.IGNORE_CASE) to { (it * 7).days },
        Regex("""(\d+)\s*tháng""", RegexOption.IGNORE_CASE) to { (it * 30).days },
        Regex("""(\d+)\s*năm""", RegexOption.IGNORE_CASE) to { (it * 365).days },
        Regex("""(\d+)\s*phút""", RegexOption.IGNORE_CASE) to { it.minutes },
        Regex("""(\d+)\s*giây""", RegexOption.IGNORE_CASE) to { it.seconds },
    )
}
