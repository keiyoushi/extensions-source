package eu.kanade.tachiyomi.extension.en.kappabeast

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document

class KappaBeast : MangaThemesia(
    "Kappa Beast",
    "https://kappabeast.com",
    "en",
    mangaUrlDirectory = "/series",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val typeFilterOptions = arrayOf(
        Pair(intl["type_filter_option_manga"], "manga"),
    )

    private val popularMangaFilter = FilterList(
        OrderByFilter("", orderByFilterOptions, "popular"),
        TypeFilter("", typeFilterOptions),
    )

    private val latestMangaFilter = FilterList(
        OrderByFilter("", orderByFilterOptions, "update"),
        TypeFilter("", typeFilterOptions),
    )

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularMangaFilter)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestMangaFilter)

    override fun searchMangaSelector() = ".listupd .maindet"

    override val seriesThumbnailSelector = ".sertothumb .ts-post-image"

    override val pageSelector = ".epcontent.entry-content img"

    override fun searchMangaParse(response: Response): MangasPage {
        if (genrelist == null) {
            genrelist = parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        }
        return super.searchMangaParse(response)
    }

    private fun parseGenres(document: Document): List<GenreData> {
        return document.select("li:has(input[id*='genre'])").map { li ->
            GenreData(
                li.selectFirst("label")!!.text(),
                li.selectFirst("input[type=checkbox]")!!.attr("value"),
            )
        }
    }
}
