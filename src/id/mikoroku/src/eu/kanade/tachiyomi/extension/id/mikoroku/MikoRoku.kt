package eu.kanade.tachiyomi.extension.id.mikoroku

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response

class MikoRoku : ZeistManga("MikoRoku", "https://www.mikoroku.my.id", "id") {

    // ============================== Popular ===============================
    override val popularMangaSelector = "div.PopularPosts div.grid > *"
    override val popularMangaSelectorTitle = "figcaption a, .post-title a"
    override val popularMangaSelectorUrl = "figcaption a, .post-title a"

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
    override val mangaDetailsSelectorGenres = "div.mt-15 > a[rel=tag]"

    // ============================ Chapter List ============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div#chapterContainer a.chap-btn").map { element ->
            SChapter.create().apply {
                name = element.selectFirst(".chap-num")?.text() ?: element.text()
                url = element.attr("href").substringAfter(baseUrl)
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        val url = if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
        return GET(url, headers)
    }

    override val pageListSelector = "div.separator"
}
