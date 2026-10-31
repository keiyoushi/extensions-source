package eu.kanade.tachiyomi.extension.vi.luvaholic

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Luvaholic : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val manga = document.select(".td-hot-slider .comic-card").mapNotNull(::mangaFromCard)
        return MangasPage(manga, hasNextPage = false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("kho-truyen")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("sort", "new")
        }.build()

        return parseMangaPage(client.get(url))
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addQueryParameter("s", query)
                addQueryParameter("paged", page.toString())
            }.build()
            return parseMangaPage(client.get(url))
        }

        val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.selectedSlug
        val statusValue = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue
        val sortValue = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: "new"

        val builder = baseUrl.toHttpUrl().newBuilder()

        if (genreSlug != null) {
            builder.addPathSegment("category")
            builder.addPathSegment(genreSlug)
            if (page > 1) {
                builder.addPathSegment("page")
                builder.addPathSegment(page.toString())
            }
            builder.addQueryParameter("sort", sortValue)
        } else {
            builder.addPathSegment("kho-truyen")
            if (page > 1) {
                builder.addPathSegment("page")
                builder.addPathSegment(page.toString())
            }
            builder.addQueryParameter("sort", sortValue)
            if (statusValue != null) builder.addQueryParameter("filter", statusValue)
        }

        return parseMangaPage(client.get(builder.build()))
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select(".row-cols-2 .col, .row .col").mapNotNull(::mangaFromCard)
        val hasNextPage = document.selectFirst("ul.page-numbers a.next") != null
        return MangasPage(manga, hasNextPage)
    }

    private fun mangaFromCard(element: Element): SManga? {
        val card = if (element.hasAttr("onclick")) element else element.selectFirst("[onclick]") ?: return null
        val onclick = card.attr("onclick")
        val url = Regex("location\\.href='([^']+)'").find(onclick)?.groupValues?.get(1)
            ?: card.selectFirst("a[href]")?.absUrl("href")
            ?: return null

        val title = card.selectFirst("h3")?.text()?.trim() ?: return null
        val thumbnail = card.selectFirst("img")?.let(::thumbnailFromImg)

        return SManga.create().apply {
            setUrlWithoutDomain(url)
            this.title = title
            thumbnail_url = thumbnail
        }
    }

    private fun thumbnailFromImg(img: Element): String? {
        img.absUrl("src").takeIf { it.isNotBlank() }?.let { return it }
        val srcset = img.attrOrNull("srcset") ?: return null
        val last = srcset.split(',').lastOrNull()?.trim()?.split(' ')?.firstOrNull()
        return last?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        var document = client.get(url).asJsoup()

        val mangaUrl = if (document.selectFirst("#chapter-list-container") == null && segments.size > 1) {
            "$baseUrl/${segments.dropLast(1).last()}/"
        } else {
            url.toString()
        }

        if (mangaUrl != url.toString()) {
            document = client.get(mangaUrl).asJsoup()
        }

        return mangaDetailsParse(document).apply {
            setUrlWithoutDomain(mangaUrl)
        }
    }

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            mangaDetailsParse(document).apply { setUrlWithoutDomain(manga.url) },
            chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("main h1")?.text()?.trim().orEmpty()
        thumbnail_url = document.selectFirst("main .ratio img.wp-post-image, main img.wp-post-image")
            ?.let(::thumbnailFromImg)

        document.select("main .row.g-3 > [class*=col-]").forEach { block ->
            val label = block.selectFirst(".small")?.text()?.trim().orEmpty()
            val value = block.selectFirst("a, strong")?.text()?.trim().orEmpty()
            when {
                label.contains("Tác giả", true) -> author = value
                label.contains("Họa sĩ", true) || label.contains("Artist", true) -> artist = value
                label.contains("Tình trạng", true) -> status = parseStatus(value)
            }
        }

        genre = document.select("main a.badge[href*=\"/category/\"]")
            .joinToString { it.text().trim() }

        val descContainer = document.selectFirst("main h3:contains(Tóm tắt nội dung) + div")
        description = descContainer?.let { container ->
            val paragraphs = container.select("p")
            if (paragraphs.isNotEmpty()) {
                paragraphs.joinToString("\n\n") { it.text().trim() }
            } else {
                container.text().trim()
            }
        }
    }

    private fun parseStatus(value: String): Int = when {
        value.contains("Hoàn thành", true) -> SManga.COMPLETED
        value.contains("Đang cập nhật", true) -> SManga.ONGOING
        value.contains("Tạm", true) -> SManga.ON_HIATUS
        value.contains("Drop", true) || value.contains("Hủy", true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun chapterListParse(document: Document): List<SChapter> = document.select("#chapter-list-container a.chap-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst("span")?.text()?.trim()
                ?: element.text().trim()
        }
    }.reversed()

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".td-chap-images img, #chapter-content-text img")
            .mapIndexedNotNull { index, img ->
                val src = img.absUrl("src").ifBlank { null }
                    ?: img.absUrl("data-src").ifBlank { null }
                    ?: return@mapIndexedNotNull null
                Page(index, imageUrl = src)
            }
    }

    // =============================== Filters ==============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        val genres = document.select(".dropdown-mega .dropdown-menu a[href*=\"/category/\"]")
            .mapNotNull { a ->
                val name = a.text().trim()
                val slug = a.absUrl("href")
                    .toHttpUrl()
                    .pathSegments
                    .filter { it.isNotBlank() }
                    .lastOrNull()
                    ?: return@mapNotNull null
                Genre(name, slug)
            }
            .distinctBy { it.slug }
            .sortedBy { it.name }

        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = parseGenreList(data)
        return FilterList(
            GenreFilter(genres),
            StatusFilter(),
            SortFilter(),
        )
    }
}
