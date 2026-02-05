package eu.kanade.tachiyomi.extension.id.roseveil

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Roseveil :
    Madara(
        "Roseveil",
        "https://roseveil.org",
        "id",
        SimpleDateFormat("MMMM dd, yyyy", Locale.US),
    ) {
    override val mangaSubString = "comic"

    // ============================== Popular & Latest ==============================
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/${searchPage(page)}?manga_order=views", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/${searchPage(page)}?manga_order=latest", headers)

    override fun popularMangaSelector() = "article"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector() = ".next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("h3 a")!!.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.text().trim()
        }
        thumbnail_url = imageFromElement(element.selectFirst("img")!!)
    }

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // =============================== Manga Details ================================
    override val mangaDetailsSelectorTitle = "h1.text-4xl"
    override val mangaDetailsSelectorThumbnail = "div.lg\\:col-span-3 img.wp-post-image"
    override val mangaDetailsSelectorDescription = ".tab-panel#panel-synopsis .prose"
    override val mangaDetailsSelectorStatus = ".tw-status-badge .tw-label"
    override val mangaDetailsSelectorAuthor = "a[href*='/author/']"

    override val seriesTypeSelector = ".flex:has(.fa-text-width) .inline-block"
    override val altNameSelector = ".mb-2 .text-gray-400"

    // =============================== Chapters =====================================
    override val useNewChapterEndpoint = false

    override fun chapterListSelector() = "#lone-ch-list li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            name = it.selectFirst("h3")?.text() ?: it.text()
        }
        date_upload = parseChapterDate(element.selectFirst("p")?.text())
    }

    override val chapterUrlSuffix = ""

    // =============================== Page List ====================================
    override val pageListParseSelector = ".reading-content .page-break img"

    // =============================== Filters ======================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$mangaSubString/${searchPage(page)}".toHttpUrl().newBuilder()
        url.addQueryParameter("manga_search", query)

        filters.forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    url.addQueryParameter("manga_order", filter.toUriPart())
                }

                is StatusSelectFilter -> {
                    if (filter.toUriPart().isNotBlank()) {
                        url.addQueryParameter("manga_status", filter.toUriPart())
                    }
                }

                is TypeSelectFilter -> {
                    if (filter.toUriPart().isNotBlank()) {
                        url.addQueryParameter("manga_type", filter.toUriPart())
                    }
                }

                is TagSelectFilter -> {
                    if (filter.toUriPart().isNotBlank()) {
                        url.addQueryParameter("manga_tag", filter.toUriPart())
                    }
                }

                is GenreSelectFilter -> {
                    if (filter.toUriPart().isNotBlank()) {
                        url.addQueryParameter("manga_genre", filter.toUriPart())
                    }
                }

                is AuthorSelectFilter -> {
                    if (filter.toUriPart().isNotBlank()) {
                        url.addQueryParameter("manga_author", filter.toUriPart())
                    }
                }

                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun getFilterList(): FilterList = FilterList(
        AuthorSelectFilter("Translator", getAuthorList()),
        OrderByFilter("Urutkan Berdasarkan", getOrderList()),
        StatusSelectFilter("Status", getStatusList()),
        TypeSelectFilter("Tipe", getTypeList()),
        TagSelectFilter("Tags", getTagList()),
        GenreSelectFilter("Genre", getGenreList()),
    )

    private fun getOrderList() = listOf(
        Pair("Latest Update", "latest"),
        Pair("Most Viewed", "views"),
        Pair("Best Rating", "rating"),
        Pair("A-Z", "title"),
    )

    private fun getStatusList() = listOf(
        Pair("Semua", ""),
        Pair("OnGoing", "on-going"),
        Pair("Completed", "end"),
        Pair("On Hold", "on-hold"),
        Pair("Canceled", "canceled"),
    )

    private fun getTypeList() = listOf(
        Pair("Semua", ""),
        Pair("Manga", "manga"),
        Pair("Manhwa", "manhwa"),
        Pair("Manhua", "manhua"),
        Pair("Comic", "comic"),
    )

    private fun getAuthorList() = listOf(
        Pair("Semua", ""),
        Pair("Anatery", "43"),
        Pair("Arata", "659"),
        Pair("arhzell", "710"),
        Pair("Ayou Rare", "52"),
        Pair("Bang Banggu", "47"),
        Pair("Black Swann", "48"),
        Pair("Curry Tale", "49"),
        Pair("Dark Espresso", "898"),
        Pair("Edelweis 20", "81"),
        Pair("Haju_Otakukomik", "727"),
        Pair("hazelqueen", "690"),
        Pair("kazuu_", "45"),
        Pair("letaak", "590"),
        Pair("Loviatar", "688"),
        Pair("Mapledesu", "3"),
        Pair("Pandy Cat", "44"),
        Pair("rose", "2"),
        Pair("Roseveil", "1"),
        Pair("Shana", "784"),
        Pair("sted", "961"),
        Pair("whatisthisac", "774"),
        Pair("Woni", "108"),
        Pair("Xiao Chen", "46"),
    )

    private fun getTagList() = listOf(
        Pair("Semua", ""),
        Pair("Historical", "historical"),
        Pair("Korea", "korea"),
        Pair("Korean", "korean"),
        Pair("Manga", "manga"),
        Pair("Manhua", "manhua"),
        Pair("Manhwa", "manhwa"),
        Pair("Mirror", "mirror"),
        Pair("Novel", "novel"),
        Pair("Project", "project"),
        Pair("Romance", "romance"),
    )

    private fun getGenreList() = listOf(
        Pair("Semua", ""),
        Pair("Action", "action"),
        Pair("Adult", "adult"),
        Pair("Adventure", "adventure"),
        Pair("Animals", "animals"),
        Pair("Boys Love", "boys-love"),
        Pair("Comedy", "comedy"),
        Pair("Demon", "demon"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Magic", "magic"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Medical", "medical"),
        Pair("Mirror", "mirror"),
        Pair("Mystery", "mystery"),
        Pair("Office Workers", "office-workers"),
        Pair("Project", "project"),
        Pair("Psychological", "psychological"),
        Pair("Regression", "regression"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Revenge", "revenge"),
        Pair("Reverse Harem", "reverse-harem"),
        Pair("Romance", "romance"),
        Pair("Royalty", "royalty"),
        Pair("School Life", "school-life"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Super Power", "super-power"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Thriller", "thriller"),
        Pair("Transmigration", "transmigration"),
        Pair("Yaoi", "yaoi"),
    )

    private open class AuthorSelectFilter(name: String, options: List<Pair<String, String>>) : UriPartFilter(name, options.toTypedArray())
    private open class StatusSelectFilter(name: String, options: List<Pair<String, String>>) : UriPartFilter(name, options.toTypedArray())
    private open class TypeSelectFilter(name: String, options: List<Pair<String, String>>) : UriPartFilter(name, options.toTypedArray())
    private open class TagSelectFilter(name: String, options: List<Pair<String, String>>) : UriPartFilter(name, options.toTypedArray())
    private open class GenreSelectFilter(name: String, options: List<Pair<String, String>>) : UriPartFilter(name, options.toTypedArray())

    override fun parseGenres(document: Document): List<Genre> = emptyList()
}
