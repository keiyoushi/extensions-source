package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class MikoRoku : ZeistManga("MikoRoku", "https://www.mikoroku.top", "id") {

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override val hasFilters = true
    override val hasLanguageFilter = false
    override val hasTypeFilter = false

    override fun getStatusList() = listOf(
        Status("Semua", ""),
        Status("Ongoing", "Ongoing"),
        Status("Completed", "Completed"),
        Status("Hiatus", "Hiatus"),
        Status("Dropped", "Dropped"),
    )

    override fun getGenreList() = listOf(
        Genre("Action", "Action"),
        Genre("Adventure", "Adventure"),
        Genre("Comedy", "Comedy"),
        Genre("Dark Fantasy", "Dark Fantasy"),
        Genre("Drama", "Drama"),
        Genre("Fantasy", "Fantasy"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Isekai", "Isekai"),
        Genre("Magic", "Magic"),
        Genre("Mecha", "Mecha"),
        Genre("Military", "Military"),
        Genre("Mystery", "Mystery"),
        Genre("Psychological", "Psychological"),
        Genre("Romance", "Romance"),
        Genre("School Life", "School Life"),
        Genre("Sci-Fi", "Sci-Fi"),
        Genre("Seinen", "Seinen"),
        Genre("Shounen", "Shounen"),
        Genre("Slice of Life", "Slice of Life"),
        Genre("Supernatural", "Supernatural"),
        Genre("Survival", "Survival"),
        Genre("Tragedy", "Tragedy"),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val header = document.selectFirst("header[itemprop=mainEntity]")
            ?: document.selectFirst("header.bg-white")!!

        return SManga.create().apply {
            thumbnail_url = header.selectFirst("img.thumb")?.attr("abs:src")
            title = header.selectFirst("h1[itemprop=name]")?.text()!!
            status = parseStatus(header.selectFirst("span[data-status]")?.text()!!)
            description = document.selectFirst("#synopsis")?.ownText()?.trim()
            author = document.select("#extra-info .y6x11p")
                .firstOrNull { it.ownText().contains("Author", ignoreCase = true) }
                ?.selectFirst("span.dt")?.text()
        }
    }

    override val chapterCategory: String = "Chapter"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = when {
            document.selectFirst("div.check-box") != null ->
                document.select("div.check-box div.separator img[src]")
            document.selectFirst("div[data=imageProtection]") != null ->
                document.select("div[data=imageProtection] div.separator img[src]")
            document.selectFirst("#post-body div.separator") != null ->
                document.select("#post-body div.separator img[src]")
            else ->
                document.select(".post-body div.separator img[src]")
        }

        return images.mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }
}
