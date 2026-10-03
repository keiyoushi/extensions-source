package eu.kanade.tachiyomi.extension.pt.mangalivreblog

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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaLivreBlog : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds)

    // ============================== Popular ================================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/manga/page/$page/?ordem=popular").asJsoup()

        val mangas = document.select(".manga-archive-grid article.home-manga-card").map(::mangaFromCard)
        val hasNextPage = document.selectFirst(".manga-archive-pagination a.next") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ================================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/?atualizacoes=$page").asJsoup()

        val mangas = document.select(".home-latest-grid article.home-manga-card").map(::mangaFromCard)
        val hasNextPage = document.selectFirst(".home-latest-pagination span.is-current + a") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromCard(el: Element) = SManga.create().apply {
        val link = el.selectFirst("h3 a")!!
        title = link.text()
        setUrlWithoutDomain(link.attr("abs:href"))
        thumbnail_url = el.selectFirst("img")?.attr("abs:src")?.let(::cleanThumbnailUrl)
    }

    // ============================== Search ================================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            } else {
                addPathSegment("pesquisa")
                addQueryParameter("s", "")
            }

            filters.forEach { filter ->
                when (filter) {
                    is RatingMinFilter -> addQueryParameter("rating_min", filter.selectedValue())
                    is SortFilter -> addQueryParameter("sort", filter.selectedValue())
                    is OrderFilter -> addQueryParameter("order", filter.selectedValue())
                    else -> {}
                }
            }

            if (page > 1) {
                addQueryParameter("paged", page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select(".manga-grid.search-results-grid .manga-card").map { el ->
            SManga.create().apply {
                val img = el.selectFirst("img")
                // The advanced search page renders every card title as "pesquisa"
                title = img?.attr("alt")?.ifEmpty { null } ?: el.selectFirst("h3.manga-card-title")!!.text()
                setUrlWithoutDomain(el.selectFirst("a.manga-card-link")!!.attr("abs:href"))
                thumbnail_url = img?.attr("abs:src")?.let(::cleanThumbnailUrl)
            }
        }

        val hasNextPage = document.selectFirst("a.next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ================================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = manga.apply {
            title = document.selectFirst("h1.manga-title")!!.text()
            document.selectFirst(".manga-cover img")?.attr("abs:src")?.let { thumbnail_url = it }
            description = document.selectFirst(".synopsis-content")?.text()
            genre = document.select(".manga-tags a").joinToString { it.text() }

            document.select(".manga-meta-item").forEach { item ->
                val label = item.selectFirst(".meta-label")?.text() ?: return@forEach
                val value = item.selectFirst(".meta-value")?.text() ?: return@forEach

                when (label) {
                    "Status:" -> status = parseStatus(value)
                    "Autor:" -> author = value
                    "Artista:" -> artist = value
                }
            }
        }

        val updatedChapters = if (fetchChapters) fetchChapterList(document) else chapters

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchChapterList(document: Document): List<SChapter> {
        val container = document.selectFirst(".chapters-container") ?: return emptyList()
        val pageSize = container.attr("data-page-size").toIntOrNull() ?: 12
        val total = container.attr("data-total-chapters").toIntOrNull() ?: 0
        val pages = (total + pageSize - 1) / pageSize

        val chapters = parseChapters(container).toMutableList()
        for (page in 2..pages) {
            val url = container.attr("data-ajax-url").toHttpUrl().newBuilder()
                .addQueryParameter("action", "slimeread_manga_chapters")
                .addQueryParameter("manga_id", container.attr("data-manga-id"))
                .addQueryParameter("nonce", container.attr("data-nonce"))
                .addQueryParameter("page", page.toString())
                .addQueryParameter("search", "")
                .build()
            val markup = client.get(url).parseAs<ChaptersResponse>().data.markup
            chapters += parseChapters(Jsoup.parseBodyFragment(markup, baseUrl))
        }

        return chapters
    }

    private fun parseChapters(element: Element) = element.select(".chapters-list .chapter-item").map { el ->
        SChapter.create().apply {
            name = el.selectFirst(".chapter-number")?.text() ?: "Capítulo"
            setUrlWithoutDomain(el.selectFirst(".chapter-link")!!.attr("abs:href"))
            date_upload = parseRelativeDate(el.selectFirst(".chapter-date")?.text())
        }
    }

    private fun parseStatus(status: String) = when (status) {
        "Em Lançamento", "Em Andamento" -> SManga.ONGOING
        "Completo" -> SManga.COMPLETED
        "Cancelado" -> SManga.CANCELLED
        "Pausado", "Hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".chapter-image-container img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    // ============================== Helpers ================================
    private fun cleanThumbnailUrl(url: String): String = url.replace("-150x150", "")

    private fun parseRelativeDate(date: String?): Long {
        if (date.isNullOrEmpty()) return 0L

        val number = Regex("""\d+""").find(date)?.value?.toIntOrNull() ?: return 0L
        val cal = Calendar.getInstance()

        return when {
            date.contains("ano", true) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            date.contains("mês", true) || date.contains("meses", true) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("semana", true) -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
            date.contains("dia", true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hora", true) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minuto", true) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            else -> 0L
        }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()
}
