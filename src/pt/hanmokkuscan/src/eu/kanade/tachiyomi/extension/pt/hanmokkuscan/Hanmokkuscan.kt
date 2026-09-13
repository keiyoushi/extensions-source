package eu.kanade.tachiyomi.extension.pt.hanmokkuscan

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class Hanmokkuscan : ZeistManga() {

    override val mangaCategory = "Todos os Projetos"
    override val chapterCategory = "Capítulo"

    override val hasFilters = true
    override val hasStatusFilter = false
    override val hasTypeFilter = false
    override val hasLanguageFilter = false
    override val hasGenreFilter = true

    override fun searchMangaUrl(page: Int, query: String) = if (query.isNotBlank()) {
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
    } else {
        super.searchMangaUrl(page, query)
    }

    override fun parseSearchManga(response: Response): MangasPage {
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

        return super.parseSearchManga(response)
    }

    override fun getGenreList() = listOf(
        "Ação", "Adulto", "Aventura", "Comédia",
        "Drama", "Ecchi", "Esporte", "Fantasia",
        "Harém", "Horror", "Isekai", "Mistério",
        "Musical", "Psicológico", "Romance", "Sci-Fi",
        "Slice of Life", "Sobrenatural",
    ).map { Genre(it, it) }
}
