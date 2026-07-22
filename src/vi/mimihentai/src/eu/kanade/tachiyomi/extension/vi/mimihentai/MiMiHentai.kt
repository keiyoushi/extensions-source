package eu.kanade.tachiyomi.extension.vi.mimihentai

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
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MiMiHentai : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 429 && response.request.url.host == baseUrl.toHttpUrl().host) {
                response.close()
                throw IOException("Bạn đang request quá nhanh!")
            }
            response
        }
        rateLimit(14, 1.minutes) { it.host == baseUrl.toHttpUrl().host }
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/danh-sach?sort=-views&page=$page", page)

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$baseUrl/danh-sach?page=$page", page)

    private suspend fun getMangaList(url: String, page: Int): MangasPage = mangaListParse(client.get(url).asJsoup(), page)

    private fun mangaListParse(document: Document, page: Int): MangasPage {
        val mangaList = document.select("a.group").mapNotNull { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("h1")?.text()?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                thumbnail_url = element.selectFirst("img")?.let(::imageUrl)
            }
        }
        val hasNextPage = document.selectFirst("a[href*='page=${page + 1}']") != null
        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val selectedGenres = filter.state.filter { it.state }.joinToString(",") { it.id }
                        if (selectedGenres.isNotEmpty()) {
                            addQueryParameter("filter[accept_genres]", selectedGenres)
                        }
                    }

                    is StatusFilter -> if (filter.state > 0) {
                        addQueryParameter("filter[status]", filter.toUriPart())
                    }

                    is SortFilter -> addQueryParameter("sort", filter.toUriPart())

                    else -> Unit
                }
            }
        }.build()

        return mangaListParse(client.get(url).asJsoup(), page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen") {
            return null
        }

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // =============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("div.title p")!!.text()
        thumbnail_url = document.selectFirst("img.rounded.shadow-md.w-full")?.let(::imageUrl)
        author = document.selectFirst("a[href*='/tac-gia/']")?.text()
        genre = document.select("a[href*='/the-loai/']").joinToString { it.text() }

        val bodyText = document.body().text()
        status = when {
            bodyText.contains("Đã hoàn thành") -> SManga.COMPLETED
            bodyText.contains("Đang tiến hành") -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        description = document.selectFirst("div.mt-4")?.ownText()
    }

    // ============================== Chapters ==============================

    private fun chapterListParse(document: Document): List<SChapter> = document.select("div.chapter-list a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst("h1")?.text()
                ?: element.attr("title").takeIf(String::isNotEmpty)
                ?: element.text()

            val dateText = element.parent()?.selectFirst("span.timeago")?.text()
                ?: element.parent()?.parent()?.selectFirst("span.timeago")?.text()
            date_upload = parseRelativeDate(dateText)
        }
    }

    private fun parseRelativeDate(value: String?): Long {
        if (value == null) return 0L
        val amount = numberRegex.find(value)?.value?.toIntOrNull() ?: return 0L
        val duration = when {
            "giây" in value -> amount.seconds
            "phút" in value -> amount.minutes
            "giờ" in value -> amount.hours
            "ngày" in value -> amount.days
            "tuần" in value -> (amount * 7).days
            "tháng" in value -> (amount * 30).days
            "năm" in value -> (amount * 365).days
            else -> return 0L
        }
        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("img.lazy")
        .mapIndexed { index, element -> Page(index, imageUrl = imageUrl(element)) }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-kiem").asJsoup()
        .select("label")
        .mapNotNull { element ->
            val id = genreIdRegex.matchEntire(element.attr("@click"))
                ?.groupValues
                ?.get(1)
                ?: return@mapNotNull null
            val name = element.text().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            GenreOption(name, id)
        }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedList = document.select("h2")
            .firstOrNull { it.text() == "Có thể bạn thích" }
            ?.parent()
            ?.nextElementSibling()
            ?: return emptyList()

        return relatedList.select("li:not(.glide__slide--clone)").mapNotNull { card ->
            val link = card.selectFirst("a.group[href*=/truyen/]") ?: return@mapNotNull null
            val title = link.selectFirst("h1")?.text()?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = link.selectFirst("img")?.let(::imageUrl)
            }
        }.distinctBy { it.url }
    }

    private fun imageUrl(element: Element): String = element.absUrl("data-src")
        .ifEmpty { element.absUrl("src") }

    private val numberRegex = Regex("\\d+")
    private val genreIdRegex = Regex("""toggleGenre\('([^']+)'\)""")
}
