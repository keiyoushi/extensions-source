package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Element

class MikoRoku : ZeistManga("MikoRoku", "https://www.mikoroku.web.id", "id") {

    // ============================== Popular ===============================
    override val popularMangaSelector = "div.PopularPosts article"
    override val popularMangaSelectorTitle = ".post-title a"
    override val popularMangaSelectorUrl = ".post-title a"

    // ============================== Filters ===============================
    override val hasFilters = true

    // The source actually has both, but they return no result, so its useless.
    override val hasLanguageFilter = false
    override val hasTypeFilter = false

    override fun getStatusList() = listOf(
        Status("Semua", ""),
        Status("Ongoing", "Ongoing"),
        Status("Completed", "Completed"),
    )

    override fun getGenreList() = listOf(
        Genre("Action", "Action"),
        Genre("Adventure", "Adventure"),
        Genre("Comedy", "Comedy"),
        Genre("Dark Fantasy", "Dark Fantasy"),
        Genre("Drama", "Drama"),
        Genre("Fantasy", "Fantasy"),
        Genre("Harem", "H4rem"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Isekai", "Isekai"),
        Genre("Magic", "Magic"),
        Genre("Mecha", "Mecha"),
        Genre("Military", "Military"),
        Genre("Monsters", "Monsters"),
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

    // =========================== Manga Details ============================
    override val mangaDetailsSelector = "div.section#main div.widget:has(main)"
    override val mangaDetailsSelectorGenres = "dl > dd > a[rel=tag]"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.use { it.asJsoup() }
        val profileManga = document.selectFirst(mangaDetailsSelector)!!
        return SManga.create().apply {
            with(profileManga) {
                thumbnail_url = selectFirst("img")?.absUrl("src")
                description = document.select(mangaDetailsSelectorDescription).text()
                genre = select(mangaDetailsSelectorGenres).eachText().joinToString()
                status = parseStatus(selectFirst("span[data-status]")?.text().orEmpty())
                author = getInfo("Author")
                artist = getInfo("Artist")
            }
        }
    }

    private fun Element.getInfo(text: String): String? =
        selectFirst("$mangaDetailsSelectorInfo:containsOwn($text) > $mangaDetailsSelectorInfoDescription")
            ?.text()
            ?.trim()

    // =============================== Pages ================================
    // Specific/faster selection first, generic/slower last
    override val pageListSelector = "article#reader div.separator a, article#reader"
}
