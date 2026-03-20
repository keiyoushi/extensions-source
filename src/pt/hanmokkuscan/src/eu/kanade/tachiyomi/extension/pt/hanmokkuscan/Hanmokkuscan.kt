package eu.kanade.tachiyomi.extension.pt.hanmokkuscan

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Hanmokkuscan : ZeistManga("Hanmokku Scan", "https://hanmokkuscan.blogspot.com", "pt-BR") {

    override val mangaCategory = "Todos os Projetos"
    override val chapterCategory = "Capítulo"

    override val hasFilters = true
    override val hasStatusFilter = false
    override val hasTypeFilter = false
    override val hasLanguageFilter = false
    override val hasGenreFilter = true

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("max-results", "20")
                .build()
            return GET(url, headers)
        }

        val url = apiUrl()
            .addQueryParameter("max-results", "999")
            .addQueryParameter("start-index", "1")

        filters.filterIsInstance<eu.kanade.tachiyomi.multisrc.zeistmanga.GenreList>()
            .flatMap { it.state }
            .filter { it.state }
            .forEach { url.addPathSegment(it.value) }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("search")) {
            val document = response.asJsoup()
            val mangas = document.select("div.grid.gtc-f141a > div").map { element ->
                SManga.create().apply {
                    title = element.selectFirst("a.ck")!!.text()
                    setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                    thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                }
            }
            return MangasPage(mangas, false)
        }

        val result = json.decodeFromString<ZeistMangaDto>(response.body.string())
        val mangas = result.feed?.entry.orEmpty()
            .filter { it.category.orEmpty().any { category -> category.term == mangaCategory } }
            .map { it.toSManga(baseUrl) }

        return MangasPage(mangas, false)
    }

    override fun getGenreList() = listOf(
        Genre("Ação", "Ação"),
        Genre("Adulto", "Adulto"),
        Genre("Aventura", "Aventura"),
        Genre("Comédia", "Comédia"),
        Genre("Drama", "Drama"),
        Genre("Ecchi", "Ecchi"),
        Genre("Esporte", "Esporte"),
        Genre("Fantasia", "Fantasia"),
        Genre("Harém", "Harém"),
        Genre("Horror", "Horror"),
        Genre("Isekai", "Isekai"),
        Genre("Mistério", "Mistério"),
        Genre("Musical", "Musical"),
        Genre("Psicológico", "Psicológico"),
        Genre("Romance", "Romance"),
        Genre("Sci-Fi", "Sci-Fi"),
        Genre("Slice of Life", "Slice of life"),
        Genre("Sobrenatural", "Sobrenatural"),
    )
}
