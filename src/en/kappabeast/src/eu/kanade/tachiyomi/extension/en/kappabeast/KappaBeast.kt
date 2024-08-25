package eu.kanade.tachiyomi.extension.en.kappabeast

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
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

    override val popularFilter = FilterList(
        OrderByFilter("", orderByFilterOptions, "popular"),
        TypeFilter("", typeFilterOptions),
    )

    override val latestFilter = FilterList(
        OrderByFilter("", orderByFilterOptions, "update"),
        TypeFilter("", typeFilterOptions),
    )

    override fun searchMangaSelector() = ".listupd .maindet"

    override val seriesThumbnailSelector = ".sertothumb .ts-post-image"

    override val pageSelector = ".epcontent.entry-content img"

    override fun parseGenres(document: Document): List<GenreData> {
        return document.select("li:has(input[id*='genre'])").map { li ->
            GenreData(
                li.selectFirst("label")!!.text(),
                li.selectFirst("input[type=checkbox]")!!.attr("value"),
            )
        }
    }
}
