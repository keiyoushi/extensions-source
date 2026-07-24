package eu.kanade.tachiyomi.extension.pt.slimereadunoriginal

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Source
abstract class SlimeReadUnoriginal : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/populares?page=$page").asJsoup()
        val entries = document.select("article.popular-card").map(::mangaFromCard)
        return MangasPage(entries, document.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/atualizacoes?page=$page").asJsoup()
        val entries = document.select("article.latest-manga-card").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a.latest-title-link")!!
                title = link.text()
                url = link.attr("href")
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(entries, document.hasNextPage())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/catalogo".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            filters.filterIsInstance<UrlPartFilter>().forEach { it.addToUrl(this) }
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        val entries = document.select("article.search-result-card").map(::mangaFromCard)
        return MangasPage(entries, document.hasNextPage())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "manga") {
            return null
        }

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val mangaUrl = "/manga/$slug"
        val document = client.get(baseUrl + mangaUrl).asJsoup()

        return parseMangaDetails(document).apply {
            this.url = mangaUrl
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".manga-details h1")!!.text()
        thumbnail_url = document.selectFirst(".manga-side > img")?.absUrl("src")
        description = document.selectFirst("p[data-synopsis]")?.text()
        genre = document.select(".manga-meta-grid .tags span")
            .map { it.text() }
            .distinct()
            .joinToString()
        status = when (document.selectFirst(".manga-info-pill-status")?.text()?.lowercase()) {
            "em andamento" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("article[data-chapter-item]").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst("a.chapter-main-link")!!
            name = link.selectFirst(".chapter-title-line")!!.text()
            url = link.attr("href")
            chapter_number = element.attr("data-chapter-number").toFloatOrNull() ?: -1f
            date_upload = parseRelativeDate(link.selectFirst(".chapter-age")?.text())
        }
    }

    private fun parseRelativeDate(date: String?): Long {
        date ?: return 0L

        if (date.startsWith("agora")) {
            return System.currentTimeMillis()
        }

        val number = date.takeWhile(Char::isDigit).toIntOrNull() ?: return 0L
        val unit = date.dropWhile(Char::isDigit).trim()

        val duration = when {
            unit.startsWith("min") -> number.minutes
            unit.startsWith("h") -> number.hours
            unit.startsWith("d") -> number.days
            unit.startsWith("sem") -> (number * 7).days
            unit.startsWith("m") -> (number * 30).days
            unit.startsWith("a") -> (number * 365).days
            else -> return 0L
        }

        return System.currentTimeMillis() - duration.inWholeMilliseconds
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        return document.select(".reader-content img[src]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
        AdultFilter(),
        SortFilter(),
    )

    private fun mangaFromCard(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        url = element.selectFirst("a[href^=/manga/]")!!.attr("href")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private fun Document.hasNextPage() = selectFirst("a.public-page-link:contains(Proxima)") != null
}
