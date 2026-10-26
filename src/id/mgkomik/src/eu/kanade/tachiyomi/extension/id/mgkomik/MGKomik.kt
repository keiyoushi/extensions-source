package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.util.Calendar

@Source
abstract class MGKomik : KeiSource() {

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("komik")
            .addPathSegment("")
            .addQueryParameter("filter", "")
            .addQueryParameter("order_by", "trending")
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url).asJsoup()
        return parseMangaListing(document)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("komik")
            .addPathSegment("")
            .addQueryParameter("filter", "")
            .addQueryParameter("order_by", "latest")
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url).asJsoup()
        return parseMangaListing(document)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addPathSegment("")
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        val value = filter.getValue()
                        if (value.isNotBlank() && value != "relevance") {
                            addQueryParameter("order_by", value)
                        }
                    }
                    is StatusFilter -> {
                        val value = filter.getValue()
                        if (value.isNotBlank()) {
                            addQueryParameter("status", value)
                        }
                    }
                    is OperatorFilter -> {
                        val value = filter.getValue()
                        if (value.isNotBlank()) {
                            addQueryParameter("op", value)
                        }
                    }
                    is TypeFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("types[]", it.value)
                        }
                    }
                    is GenreFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("genres[]", it.value)
                        }
                    }
                    else -> {}
                }
            }
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        return parseMangaListing(document)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        OperatorFilter(),
        GenreFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url).asJsoup()
        return parseMangaDetails(document, SManga.create().apply { this.url = url.encodedPath })
    }

    private fun parseMangaListing(document: Document): MangasPage {
        val mangaCards = document.select(".manga-card")
        val mangas = mangaCards.mapNotNull { element ->
            val link = element.selectFirst("a[href]") ?: element.takeIf { it.tagName() == "a" } ?: return@mapNotNull null
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val path = if (href.startsWith("http")) href.toHttpUrl().encodedPath else href

            SManga.create().apply {
                url = path
                title = element.selectFirst("img.manga-cover")?.attr("alt")
                    ?: element.selectFirst(".manga-title")?.text()!!
                thumbnail_url = element.selectFirst("img.manga-cover")?.attr("abs:src")
            }
        }.distinctBy { it.url }

        val hasNextPage = document.select(".pagination a.page-link").any { it.text().contains("Next", ignoreCase = true) }
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        url = manga.url
        title = document.selectFirst(".manga-title")?.text() ?: manga.title
        thumbnail_url = document.selectFirst("img.manga-cover-large")?.attr("abs:src") ?: manga.thumbnail_url
        description = document.selectFirst("#sinopsisContainer p, .manga-description .sinopsis-wrapper p")?.text()
        genre = document.select(".genre-tag").joinToString { it.text().trim() }
        status = when {
            document.selectFirst(".status-Ongoing, .status-ongoing") != null -> SManga.ONGOING
            document.selectFirst(".status-Completed, .status-completed") != null -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select("#chapterList .chapter-list-item").mapNotNull { element ->
            val link = element.selectFirst("a.chapter-link") ?: return@mapNotNull null
            val href = link.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val path = if (href.startsWith("http")) href.toHttpUrl().encodedPath else href

            SChapter.create().apply {
                url = path
                name = link.selectFirst(".chapter-number")?.text() ?: link.text()
                date_upload = parseRelativeDate(link.selectFirst(".chapter-date")?.text().orEmpty())
            }
        }
    }

    private fun getUrl(path: String): String = "$baseUrl/${path.removePrefix("/")}"

    override fun getMangaUrl(manga: SManga): String = getUrl(manga.url)

    override fun getChapterUrl(chapter: SChapter): String = getUrl(chapter.url)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getUrl(manga.url)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document, manga), parseChapterList(document))
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getUrl(chapter.url)).asJsoup()
        return document.select(".reading-content img[src]").mapIndexed { index, img ->
            val imageUrl = img.attr("abs:src")
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun parseRelativeDate(dateStr: String): Long {
        val number = Regex("""(\d+)""").find(dateStr)?.value?.toIntOrNull() ?: return 0L
        val now = Calendar.getInstance()
        when {
            dateStr.contains("second", ignoreCase = true) || dateStr.contains("detik", ignoreCase = true) -> now.add(Calendar.SECOND, -number)
            dateStr.contains("minute", ignoreCase = true) || dateStr.contains("menit", ignoreCase = true) -> now.add(Calendar.MINUTE, -number)
            dateStr.contains("hour", ignoreCase = true) || dateStr.contains("jam", ignoreCase = true) -> now.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("day", ignoreCase = true) || dateStr.contains("hari", ignoreCase = true) -> now.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("week", ignoreCase = true) || dateStr.contains("minggu", ignoreCase = true) -> now.add(Calendar.WEEK_OF_MONTH, -number)
            dateStr.contains("month", ignoreCase = true) || dateStr.contains("bulan", ignoreCase = true) -> now.add(Calendar.MONTH, -number)
            dateStr.contains("year", ignoreCase = true) || dateStr.contains("tahun", ignoreCase = true) -> now.add(Calendar.YEAR, -number)
            else -> return 0L
        }
        return now.timeInMillis
    }
}
