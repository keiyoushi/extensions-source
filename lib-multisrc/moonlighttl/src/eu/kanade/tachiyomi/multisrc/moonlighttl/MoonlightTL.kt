package eu.kanade.tachiyomi.multisrc.moonlighttl

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class MoonlightTL : KeiSource() {
    protected val intl = Intl(
        lang,
        setOf("en", "es"),
        "en",
        this::class.java.classLoader!!,
    )

    private val seriesPath = "/ver"

    // novel chapters are text without images, so they always end up as "No pages found"
    private suspend fun getComics(): List<SeriesDto> = client.get("$baseUrl/api/comics").parseAs<ResponseDto<List<SeriesDto>>>().response
        .filterNot { it.isNovel }

    // /api/topSerie has no type field to filter novels with
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = getComics()
            .sortedByDescending { it.trending?.monthlyViews ?: 0 }
            .map { it.toSManga(seriesPath) }

        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val responseData = client.get("$baseUrl/api/lastUpdates").parseAs<ResponseDto<List<SeriesDto>>>()

        val mangas = responseData.response
            .filterNot { it.isNovel }
            .map { it.toSManga(seriesPath) }

        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = applyFilters(getComics(), query, filters)

    private fun applyFilters(comics: List<SeriesDto>, query: String, filterList: FilterList): MangasPage {
        var filteredList = mutableListOf<SeriesDto>()

        if (query.isNotBlank()) {
            if (query.length < 2) throw Exception(intl["search_length_error"])
            filteredList.addAll(
                comics.filter {
                    it.name.contains(query, ignoreCase = true) || it.alternativeName?.contains(query, ignoreCase = true) == true
                },
            )
        } else {
            filteredList.addAll(comics)
        }

        val statusFilter = filterList.firstInstanceOrNull<StatusFilter>()

        if (statusFilter != null) {
            if (statusFilter.toUriPart() != 0) {
                filteredList = filteredList.filter { it.status == statusFilter.toUriPart() }.toMutableList()
            }
        }

        val sortByFilter = filterList.firstInstanceOrNull<SortByFilter>()

        if (sortByFilter != null) {
            when (sortByFilter.selected) {
                "name" -> filteredList.sortBy { it.name }
                "views" -> filteredList.sortBy { it.trending?.views }
                "updated_at" -> filteredList.sortBy { it.lastChapterDate }
                "created_at" -> filteredList.sortBy { it.createdAt }
            }

            if (sortByFilter.state?.ascending == false) {
                filteredList.reverse()
            }
        }

        return MangasPage(filteredList.map { it.toSManga(seriesPath) }, false)
    }

    override fun getFilterList(data: JsonElement?) = getFilters(intl)

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    // details and chapters come from the same endpoint
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast('/')
        val series = client.get("$baseUrl/api/showProject/$slug").parseAs<ResponseDto<SeriesDto>>().response
        return SMangaUpdate(
            series.toSMangaDetails(intl),
            series.chapters.map { it.toSChapter(seriesPath, series.slug, intl) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(getChapterUrl(chapter)).asJsoup())

    protected open suspend fun pageListParse(document: Document): List<Page> {
        var doc = document
        val form = doc.selectFirst("form[method=post]")
        if (form != null) {
            val url = form.attr("action")
            val headers = headersBuilder().set("Referer", doc.location()).build()
            val body = FormBody.Builder()
            form.select("input").forEach {
                body.add(it.attr("name"), it.attr("value"))
            }
            doc = client.post(url, headers, body.build()).asJsoup()
        }
        return doc.select("main.contenedor.read img, main > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.imgAttr())
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }
}
