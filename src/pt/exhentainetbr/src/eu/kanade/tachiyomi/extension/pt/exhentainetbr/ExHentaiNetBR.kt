package eu.kanade.tachiyomi.extension.pt.exhentainetbr

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ExHentaiNetBR : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/lista-de-mangas/page/$page").asJsoup()
        return parseMangaList(document, "article.itemP")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var url = "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()

        val filter = filters.firstInstanceOrNull<AlphabetFilter>()

        if (query.isBlank() && filter != null && filter.selected() != DEFAULT_FILTER_VALUE) {
            url = "$baseUrl/lista-de-mangas".toHttpUrl().newBuilder()
                .addQueryParameter("letra", filter.selected())
                .build()
        }

        val document = client.get(url).asJsoup()
        val isLetterSearch = url.queryParameter("letra") != null
        val selector = if (isLetterSearch) "article.itemP" else ".post article.itemP"

        return parseMangaList(document, selector)
    }

    private fun parseMangaList(document: Document, selector: String): MangasPage {
        val mangas = document.select(selector).map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.ownText()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst(".content-pagination li.active + li:not(.next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2) {
            return null
        }
        val slug = url.pathSegments[1]
        val document = client.get("$baseUrl/manga/$slug").asJsoup()

        return SManga.create().apply {
            parseDetails(document)
            setUrlWithoutDomain(document.location())
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = manga.apply { parseDetails(document) }

        val updatedChapters = document.select("a.ex-chapter-row").map { element ->
            SChapter.create().apply {
                name = element.selectFirst(".ex-ch-left")!!.text()
                date_upload = dateFormat.tryParseDate(element.selectFirst(".ex-ch-date")?.text())
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun SManga.parseDetails(document: Document) {
        title = document.selectFirst(".ex-manga-titles h1")!!.text()
        description = document.selectFirst(".ex-manga-synopsis .gerens-content p")?.text()
        thumbnail_url = document.selectFirst(".ex-manga-cover-box img")?.imgAttr()
        artist = document.selectFirst(".ex-meta-item:has(> span:contains(Artista)) a")?.text()
        author = artist
        genre = document.select(".ex-tags-section a.ex-badge-tag").joinToString { it.text() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.manga_image > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgAttr())
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val alphabet = mutableListOf(DEFAULT_FILTER_VALUE).also {
            it += ('A'..'Z').map { char -> "$char" }
        }

        return FilterList(
            Filter.Header(
                """
                    Busca por título possue prioridade.
                    Deixe em branco para pesquisar por letra
                """.trimIndent(),
            ),
            AlphabetFilter("Alfabeto", alphabet),
        )
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
        const val DEFAULT_FILTER_VALUE = "Padrão"
    }
}

class AlphabetFilter(
    displayName: String,
    private val vals: List<String>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.toTypedArray(), state) {
    fun selected() = vals[state]
}
