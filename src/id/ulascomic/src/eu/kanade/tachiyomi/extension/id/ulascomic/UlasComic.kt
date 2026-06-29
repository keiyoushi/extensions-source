package eu.kanade.tachiyomi.extension.id.ulascomic

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class UlasComic : ZeistManga("Ulas Comic", "https://www.ulascomic01.xyz", "id") {

    // ============================== Popular ===============================
    override val popularMangaSelector = "div.serieslist.pop.wpop ul li"
    override val popularMangaSelectorTitle = ".leftseries h2 a"
    override val popularMangaSelectorUrl = ".leftseries h2 a"

    override fun popularMangaRequest(page: Int): Request = if (page == 1) {
        GET(baseUrl, headers)
    } else {
        val startIndex = 20 * (page - 1) + 1
        val url = apiUrl()
            .addQueryParameter("max-results", "21")
            .addQueryParameter("start-index", startIndex.toString())
            .build()
        GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().contains("alt=json")) {
            return searchMangaParse(response)
        }

        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
                title = element.selectFirst(popularMangaSelectorTitle)!!.text()
                setUrlWithoutDomain(element.selectFirst(popularMangaSelectorUrl)!!.attr("href"))
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val startIndex = 20 * (page - 1) + 1
        val url = apiUrl()
            .addQueryParameter("orderby", "updated")
            .addQueryParameter("max-results", "21")
            .addQueryParameter("start-index", startIndex.toString())
            .build()

        return GET(url, headers)
    }

    // ============================== Filters ===============================
    override val hasFilters = true
    override val hasLanguageFilter = false

    override fun getStatusList() = listOf(
        Status("All", ""),
        Status("Completed", "Completed"),
        Status("Ongoing", "Ongoing"),
    )

    override fun getTypeList() = listOf(
        Type("All", ""),
        Type("Manga", "Manga"),
        Type("Manhua", "Manhua"),
        Type("Manhwa", "Manhwa"),
    )

    override fun getGenreList() = listOf(
        Genre("Action", "Action"),
        Genre("Adventure", "Adventure"),
        Genre("Comedy", "Comedy"),
        Genre("Crime", "Crime"),
        Genre("Drama", "Drama"),
        Genre("Ecchi", "Ecchi"),
        Genre("Fantasy", "Fantasy"),
        Genre("Harem", "Harem"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Josei", "Josei"),
        Genre("Martial Arts", "Martial Arts"),
        Genre("Medical", "Medical"),
        Genre("Military", "Military"),
        Genre("Music", "Music"),
        Genre("Mystery", "Mystery"),
        Genre("One Shot", "One Shot"),
        Genre("Police", "Police"),
        Genre("Psychological", "Psychological"),
        Genre("Reincarnation", "Reincarnation"),
        Genre("Revenge", "Revenge"),
        Genre("Romance", "Romance"),
        Genre("School Life", "School Life"),
        Genre("Sci-Fi", "Sci-Fi"),
        Genre("Seinen", "Seinen"),
        Genre("Shounen", "Shounen"),
        Genre("Slice of Life", "Slice of Life"),
        Genre("Sports", "Sports"),
        Genre("Supernatural", "Supernatural"),
        Genre("Survival", "Survival"),
        Genre("Thriller", "Thriller"),
        Genre("Time Travel", "Time Travel"),
        Genre("Tragedy", "Tragedy"),
        Genre("Vampire", "Vampire"),
    )

    // =========================== Manga Details ============================
    override val mangaDetailsSelector = ".animefull"
    override val mangaDetailsSelectorDescription = ".wd-full p"
    override val mangaDetailsSelectorGenres = ".mgen a"
    override val mangaDetailsSelectorStatus = ".imptdt:contains(Status) i"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val profileManga = document.selectFirst(mangaDetailsSelector)!!

        return SManga.create().apply {
            title = document.selectFirst("h1.entry-title")!!.text()
            thumbnail_url = profileManga.selectFirst("img")!!.attr("abs:src")
            description = profileManga.select(mangaDetailsSelectorDescription).first()!!.text()
            genre = profileManga.select(mangaDetailsSelectorGenres).joinToString { it.text() }
            author = profileManga.selectFirst("span[data-perfect-post='author']")!!.text()
            status = parseStatus(profileManga.selectFirst(mangaDetailsSelectorStatus)!!.text())
        }
    }

    // ============================== Chapters ==============================
    override val chapterCategory = "Manga Chapter"

    override fun getChapterFeedUrl(doc: Document): String {
        val title = doc.selectFirst("h1.entry-title")!!.text()

        return apiUrl(title)
            .addQueryParameter("max-results", "999999")
            .build().toString()
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(config['chapterImage'])")
        if (script != null) {
            val imageUrls = IMAGE_REGEX.findAll(script.data().substringAfter("config['chapterImage']"))
                .map { it.groupValues[1] }
                .toList()

            if (imageUrls.isNotEmpty()) {
                return imageUrls.mapIndexed { i, url ->
                    Page(i, "", url)
                }
            }
        }

        return document.select(pageListSelector).select("img[src]").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    companion object {
        private val IMAGE_REGEX = """"(https?://.*?)"""".toRegex()
    }
}
