package eu.kanade.tachiyomi.extension.es.ikuhentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

@Source
abstract class Ikuhentai : KeiSource() {

    private val dateFormat = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("MMMM d, yyyy")
        .toFormatter(Locale("es"))

    override suspend fun getPopularManga(page: Int): MangasPage {
        val pagePath = if (page > 1) "page/$page/" else ""
        return parseMangaList(client.get("$baseUrl/$pagePath?s=&post_type=wp-manga&m_orderby=views").asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val pagePath = if (page > 1) "page/$page/" else ""
        return parseMangaList(client.get("$baseUrl/$pagePath?s=&post_type=wp-manga&m_orderby=latest").asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.page-listing-item .page-item-detail, div.c-tabs-item__content").map {
            mangaFromElement(it)
        }
        val hasNextPage = document.selectFirst("a.nextpostslink, div.nav-previous > a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val img = element.selectFirst("img")
        manga.thumbnail_url = img?.let {
            it.absUrl("data-lazy-src").ifEmpty { it.absUrl("src") }
        }

        val link = element.selectFirst("div.item-thumb > a, div.tab-thumb > a")
        if (link != null) {
            manga.setUrlWithoutDomain(link.absUrl("href"))
            manga.title = link.attr("title").ifEmpty { link.text() }
        }
        return manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
                addPathSegment("") // for trailing slash
            }
            addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")

            filters.forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        filter.state.filter { it.state == Filter.TriState.STATE_INCLUDE }.forEach {
                            addQueryParameter("genre[]", it.id)
                        }
                    }
                    is StatusList -> {
                        filter.state.filter { it.state == Filter.TriState.STATE_INCLUDE }.forEach {
                            addQueryParameter("status[]", it.id)
                        }
                    }
                    is SortBy -> {
                        val orderBy = filter.toUriPart()
                        if (orderBy.isNotEmpty()) {
                            addQueryParameter("m_orderby", orderBy)
                        }
                    }
                    is TextField -> {
                        if (filter.state.isNotEmpty()) {
                            addQueryParameter(filter.key, filter.state)
                        }
                    }
                    else -> {}
                }
            }
        }

        return parseMangaList(client.get(url.build()).asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val infoElement = document.selectFirst("div.site-content") ?: document

        return SManga.create().apply {
            title = manga.title
            author = infoElement.select("div.author-content").text()
            artist = infoElement.select("div.artist-content").text()

            val genres = infoElement.select("div.genres-content a").map { it.text() }
            genre = genres.joinToString(", ")

            val statusText = infoElement.select("div.post-content_item:has(h5:contains(Estado)) div.summary-content").text()
            status = parseStatus(statusText)

            description = document.select("div.description-summary").text()

            val img = document.selectFirst("div.summary_image img")
            thumbnail_url = img?.let {
                it.absUrl("data-lazy-src").ifEmpty { it.absUrl("src") }
            }
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase().contains("ongoing") || element.lowercase().contains("emisión") || element.lowercase().contains("emision") -> SManga.ONGOING
        element.lowercase().contains("completado") || element.lowercase().contains("finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val url = (baseUrl + manga.url).removeSuffix("/")
        val document = client.post("$url/ajax/chapters/", headers, FormBody.Builder().build()).asJsoup()

        return document.select("li.wp-manga-chapter").map { element ->
            val urlElement = element.selectFirst("a")!!
            val chapterUrl = urlElement.absUrl("href").toHttpUrl().newBuilder().apply {
                removeAllQueryParameters("style")
                addQueryParameter("style", "list")
            }.build().toString()

            SChapter.create().apply {
                setUrlWithoutDomain(chapterUrl)
                name = urlElement.text()

                val dateElement = element.selectFirst("span.chapter-release-date i")
                if (dateElement != null) {
                    date_upload = dateFormat.tryParseDate(dateElement.text())
                }
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.reading-content * img").mapIndexedNotNull { i, element ->
            val url = element.absUrl("data-lazy-src").ifEmpty { element.absUrl("src") }
            if (url.isNotEmpty()) Page(i, imageUrl = url) else null
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TextField("Autor", "author"),
        TextField("Año de publicación", "release"),
        SortBy(),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
    )
}
