package eu.kanade.tachiyomi.extension.pt.pointzerotoons

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class PointZeroToons : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get(mangaListUrl(page, "updated")).asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = sortFilterList[filters.firstInstance<SortFilter>().state].first
        val status = statusFilterList[filters.firstInstance<StatusFilter>().state].first
        val type = typeFilterList[filters.firstInstance<TypeFilter>().state].first
        val genre = genreFilterList[filters.firstInstance<GenreFilter>().state].first

        return parseMangaList(client.get(mangaListUrl(page, sort, query, status, type, genre)).asJsoup())
    }

    private fun mangaListUrl(
        page: Int,
        order: String,
        query: String = "",
        status: String = "",
        type: String = "",
        genre: String = "",
    ): String {
        val url = (if (page > 1) "$baseUrl/manga/page/$page/" else "$baseUrl/manga/").toHttpUrl().newBuilder()
        url.addQueryParameter("order", order)
        if (query.isNotEmpty()) url.addQueryParameter("s", query)
        if (status.isNotEmpty()) url.addQueryParameter("status", status)
        if (type.isNotEmpty()) url.addQueryParameter("type", type)
        if (genre.isNotEmpty()) url.addQueryParameter("genre", genre)
        return url.build().toString()
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("article.inkra-catalog-card")
            .map { card ->
                SManga.create().apply {
                    setUrlWithoutDomain(card.selectFirst("a.inkra-catalog-card__media")!!.absUrl("href"))
                    title = card.selectFirst("h3 a")!!.text()
                    thumbnail_url = card.selectFirst("img")?.absUrl("src")
                }
            }
            .filterNot { it.title.contains(NOVEL_MARKER, ignoreCase = true) }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val chapterList = document.select("article.inkra-chapter-item").map { item ->
            SChapter.create().apply {
                setUrlWithoutDomain(item.selectFirst("a.inkra-chapter-item__link")!!.absUrl("href"))
                name = item.selectFirst(".inkra-chapter-item__label")!!.text()
                chapter_number = item.attr("data-number").toFloatOrNull() ?: -1f
            }
        }

        return SMangaUpdate(parseMangaDetails(document, manga), chapterList)
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga {
        val details = document.select("dl.inkra-series-detail-list")
        fun detail(label: String): String? = details.select("dt:contains($label)").firstOrNull()
            ?.nextElementSibling()
            ?.textOrNull()

        return manga.apply {
            title = document.selectFirst("h1.inkra-page-title")!!.text()
            thumbnail_url = document.selectFirst(".inkra-series-cover img")?.absUrl("src")
            description = document.selectFirst(".inkra-series-synopsis")?.textOrNull()
            author = detail("Autor")
            genre = detail("Tipo")?.replaceFirstChar { it.uppercase() }
            status = when (detail("Status")?.lowercase()) {
                "ongoing", "on-going" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                "dropped", "canceled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("figure.inkra-reader-page img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(sortFilterList.map { it.second }.toTypedArray()),
        Filter.Separator(),
        StatusFilter(statusFilterList.map { it.second }.toTypedArray()),
        Filter.Separator(),
        TypeFilter(typeFilterList.map { it.second }.toTypedArray()),
        Filter.Separator(),
        GenreFilter(genreFilterList.map { it.second }.toTypedArray()),
    )
}

// Listing cards don't expose the type, so novels are only recognisable by the "(NOVEL)" marker the site puts in their titles.
private const val NOVEL_MARKER = "(NOVEL)"
