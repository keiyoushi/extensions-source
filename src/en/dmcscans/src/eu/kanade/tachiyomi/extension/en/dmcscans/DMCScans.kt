package eu.kanade.tachiyomi.extension.en.dmcscans

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

class DMCScans : ZeistManga("DMC Scans", "https://didascans.blogspot.com", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(1, 3, TimeUnit.SECONDS)
        .build()

    // ============================== Popular ===============================

    override val popularMangaSelector = ".PopularPosts > article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = ".post-title a"

    // ============================== Search ================================

    override val excludedCategories = listOf("Web Novel")

    // =========================== Manga Details ============================

    override val mangaDetailsSelectorGenres = "#labels > a[rel=tag]"
    override val mangaDetailsSelectorInfo = ".imptdts"
    override val mangaDetailsSelectorDescription = "p"
    override val mangaDetailsSelectorInfoDescription = "div:containsOwn(Status) > span"

    // =========================== Chapter Feed =============================

    override val chapterFeedRegex = """.run\(["'](.*?)["']\)""".toRegex()

    override fun getChapterFeedUrl(doc: Document): String {
        val feed = chapterFeedRegex
            .find(doc.html())
            ?.groupValues?.get(1)
            ?: throw Exception("Failed to find chapter feed")

        return apiUrl(chapterCategory)
            .addPathSegments(feed)
            .addQueryParameter("max-results", maxChapterResults.toString())
            .build().toString()
    }

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

        val imgData = document.selectFirst("script:containsData(imgTag)")
            ?.data()
            ?.substringAfter("imgTag")
            ?.substringAfter("`")
            ?.substringBefore("`")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace("\\/", "/")
            ?.replace("\\:", ":")
            ?.let(Jsoup::parseBodyFragment)
            ?: return document.select(pageListSelector).select("img[src]").mapIndexed { i, img ->
                Page(i, "", img.attr("abs:src"))
            }

        return imgData.select("img[src]").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }
}
