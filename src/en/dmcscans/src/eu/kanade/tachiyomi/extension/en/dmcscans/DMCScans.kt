package eu.kanade.tachiyomi.extension.en.dmcscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.Jsoup

class DMCScans : ZeistManga("DMC Scans", "https://didascans.blogspot.com", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // ============================== Popular ===============================

    override val popularMangaSelector = ".PopularPosts > article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = ".post-title a"

    // =========================== Manga Details ============================

    override val mangaDetailsSelectorGenres = "#labels > a[rel=tag]"
    override val mangaDetailsSelectorInfo = ".imptdt"

    // =============================== Filters ==============================

    override val hasFilters = true
    override val hasTypeFilter = false
    override val hasLanguageFilter = false

    override fun getGenreList(): List<Genre> = listOf(
        Genre("Adaptation", "Adaptation"),
        Genre("Drama", "Drama"),
        Genre("Historical", "Historical"),
        Genre("Josei(W)", "Josei(W)"),
        Genre("Regression", "Regression"),
        Genre("Romance", "Romance"),
        Genre("Shojo(G)", "Shojo(G)"),
        Genre("Slice of Life", "Slice of Life"),
        Genre("Transmigration", "Transmigration"),
    )

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imgData = document.selectFirst("script:containsData(imgTags)")
            ?.data()
            ?.substringAfter("imgTags")
            ?.substringAfter("`")
            ?.substringBefore("`")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace("\\/", "/")
            ?.replace("\\:", ":")
            ?.let(Jsoup::parseBodyFragment)
            ?: return super.pageListParse(response)

        return imgData.select("img[src]").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }
}
