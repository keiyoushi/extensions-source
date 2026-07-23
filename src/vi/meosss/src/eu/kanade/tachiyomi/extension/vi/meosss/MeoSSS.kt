package eu.kanade.tachiyomi.extension.vi.meosss

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class MeoSSS : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/dang-thinh-hanh/").asJsoup()
        val mangas = document.select(".manga-item-details").map { it.mangaFromPopularElement() }
        return MangasPage(mangas, hasNextPage = false)
    }

    private fun Element.mangaFromPopularElement(): SManga = SManga.create().apply {
        setUrlWithoutDomain(select("a[href*=/truyen/]").first()!!.absUrl("href"))
        title = select("h2.uk-text-bold a").first()!!.text()
        thumbnail_url = selectFirst("img")?.imgUrl()
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/moi-cap-nhat/" + if (page > 1) "page/$page/" else ""
        val document = client.get(url).asJsoup()
        val mangas = document.select(".manga-item-grid").map { it.mangaFromGridElement() }
        val hasNextPage = document.selectFirst(nextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.mangaFromGridElement(): SManga = SManga.create().apply {
        setUrlWithoutDomain(select("a[href*=/truyen/]").first()!!.absUrl("href"))
        title = select("h2.uk-text-bold a").first()!!.text()
        thumbnail_url = select("img").firstOrNull()?.imgUrl()
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            val path = "/" + if (page > 1) "page/$page/" else ""
            "$baseUrl$path".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
        } else {
            val path = "/bo-loc-nang-cao/" + if (page > 1) "page/$page/" else ""
            "$baseUrl$path".toHttpUrl().newBuilder().apply {
                filters.firstInstanceOrNull<GenreFilter>()?.state
                    ?.filter { it.state }
                    ?.forEach { addQueryParameter("genre[]", it.value) }
                filters.firstInstanceOrNull<StatusFilter>()?.let {
                    val statusValue = statusValues[it.state]
                    if (statusValue.isNotEmpty()) addQueryParameter("status", statusValue)
                }
                filters.firstInstanceOrNull<AgeRatingFilter>()?.let {
                    val ageValue = ageValues[it.state]
                    if (ageValue.isNotEmpty()) addQueryParameter("age_rating", ageValue)
                }
                filters.firstInstanceOrNull<SortFilter>()?.let {
                    addQueryParameter("sort", sortValues[it.state])
                }
            }
        }.build()

        return parseSearchManga(client.get(url))
    }

    private fun parseSearchManga(response: Response): MangasPage {
        val document = response.asJsoup()
        val isFilterPage = document.select(".manga-filter-form").firstOrNull() != null

        if (isFilterPage) {
            val mangas = document.select(".manga-item-details").map { it.mangaFromPopularElement() }
            val hasNextPage = document.selectFirst(nextPageSelector) != null
            return MangasPage(mangas, hasNextPage)
        }

        val mangas = document.select("article").mapNotNull { article ->
            val link = article.selectFirst("h2 a[href*=/truyen/]") ?: return@mapNotNull null
            val href = link.absUrl("href")
            // Exclude non-manga URLs
            if (!mangaUrlRegex.containsMatchIn(href)) return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(href)
                title = link.text()
                thumbnail_url = article.selectFirst("img")?.imgUrl()
            }
        }
        val hasNextPage = document.selectFirst(nextPageSelector) != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ===============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.select("#manga-title").first()!!.text()
        author = document.select(".manga-info-details a[href*=/tac-gia/]").firstOrNull()?.text()
        description = document.select("#manga-description").firstOrNull()?.text()
        genre = document.select(".manga-block a[href*=/the-loai/]").joinToString { it.text() }
        status = parseStatus(document.select("#manga-status").firstOrNull()?.text()) ?: 0
        thumbnail_url = document.select(".story-cover img").firstOrNull()?.imgUrl()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen/$slug/")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = if (fetchDetails) {
            async { parseMangaDetails(client.get(getMangaUrl(manga)).asJsoup(), manga) }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { fetchChapters(manga) }
        } else {
            null
        }

        SMangaUpdate(
            manga = mangaDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    private fun parseStatus(status: String?): Int? = when (status?.lowercase()) {
        "đang tiến hành", "đã theo kịp" -> SManga.ONGOING
        "trọn bộ" -> SManga.COMPLETED
        "kết thúc mùa" -> SManga.ON_HIATUS
        "nguồn tạm ngưng" -> SManga.ON_HIATUS
        "bị hủy" -> SManga.CANCELLED
        else -> null
    }

    // ============================== Chapters ===============================

    private suspend fun fetchChapters(manga: SManga): List<SChapter> {
        val mangaUrl = getMangaUrl(manga).removeSuffix("/")
        val chapters = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val doc = client.get("$mangaUrl/chap/page/$page/").asJsoup()

            val pageChapters = doc.select(".chapter-item").map { chapterFromElement(it) }
            if (pageChapters.isEmpty()) break
            chapters.addAll(pageChapters)

            val hasNext = doc.select(".uk-pagination a[href*=/chap/page/]").any {
                it.text().toIntOrNull()?.let { num -> num > page } == true
            }
            if (!hasNext) break
            page++
        }

        return chapters
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val a = element.select("a.uk-link-toggle").first()!!
        setUrlWithoutDomain(a.absUrl("href"))
        name = element.select("h3").first()!!.text().substringAfterLast('\u2013').trim()
        date_upload = Instant.parseOrNull(a.select("time[datetime]").firstOrNull()?.attr("datetime").orEmpty())
            ?.toEpochMilliseconds()
            ?: 0L
    }

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#chapter-content img").filterNot { it.closest(".init-manga-chapter-ad") != null }
            .mapIndexed { index, img ->
                Page(index, imageUrl = img.attr("data-original-src").ifEmpty { img.imgUrl() })
            }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/bo-loc-nang-cao/").asJsoup()
        .select("input[name=genre[]]")
        .map { input ->
            GenreOption(
                name = input.attr("data-genre-name").ifEmpty { input.attr("value") },
                value = input.attr("value"),
            )
        }
        .sortedBy { it.name }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>().orEmpty())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedSection = document.select("h2")
            .firstOrNull { it.text() == "Truyện liên quan" }
            ?.parent()
            ?: return emptyList()

        return relatedSection.select(".manga-item-slider").mapNotNull { element ->
            val link = element.selectFirst("a[href*=/truyen/]") ?: return@mapNotNull null
            val title = element.selectFirst("h3, h4, .manga-title")?.text()
                ?: element.selectFirst("img[alt]")?.attr("alt")?.removePrefix("Ảnh bìa của ")
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = element.selectFirst("img")?.imgUrl()
            }
        }.distinctBy { it.url }
    }

    private fun Element.imgUrl(): String? = attr("src").ifEmpty { null }
    private val nextPageSelector = ".uk-pagination a[aria-label='Trang sau'][href]"
    private val mangaUrlRegex = Regex("/truyen/[a-z0-9]+(?:-[a-z0-9]+)*/$")
    private val statusValues = arrayOf("", "ongoing", "season_end", "completed", "source_hiatus", "caught_up", "dropped")
    private val ageValues = arrayOf("", "all", "13+", "16+", "18+")
    private val sortValues = arrayOf("updated", "new", "old", "views", "views_day", "views_week", "views_month", "rating", "power", "follow")
}
