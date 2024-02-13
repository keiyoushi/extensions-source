package eu.kanade.tachiyomi.extension.en.mangakakalots

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Mangakakalots : MangaBox("Mangakakalots (unoriginal)", "https://mangakakalots.com", "en", SimpleDateFormat("MMM dd,yy", Locale.ENGLISH)) {
    override fun searchMangaSelector(): String = "${super.searchMangaSelector()}, div.list-truyen-item-wrap"
    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { mangaFromElement(it) }
        val hasNextPage = !response.request.url.toString()
            .contains(document.select(searchMangaNextPageSelector()).attr("href"))

        return MangasPage(mangas, hasNextPage)
    }
    override fun searchMangaNextPageSelector() = "div.group_page a:last-of-type"
    override fun getStatusFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("all", "ALL"),
        Pair("Completed", "Completed"),
        Pair("Ongoing", "Ongoing"),
    )
    override fun getGenreFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("all", "ALL"),
        Pair("Action", "Action"),
        Pair("Adult", "Adult"),
        Pair("Adventure", "Adventure"),
        Pair("Comedy", "Comedy"),
        Pair("Cooking", "Cooking"),
        Pair("Doujinshi", "Doujinshi"),
        Pair("Drama", "Drama"),
        Pair("Ecchi", "Ecchi"),
        Pair("Fantasy", "Fantasy"),
        Pair("Gender bender", "Gender bender"),
        Pair("Harem", "Harem"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Isekai", "Isekai"),
        Pair("Josei", "Josei"),
        Pair("Manhua", "Manhua"),
        Pair("Manhwa", "Manhwa"),
        Pair("Martial arts", "Martial arts"),
        Pair("Mature", "Mature"),
        Pair("Mecha", "Mecha"),
        Pair("Medical", "Medical"),
        Pair("Mystery", "Mystery"),
        Pair("One shot", "One shot"),
        Pair("Psychological", "Psychological"),
        Pair("Romance", "Romance"),
        Pair("School life", "School life"),
        Pair("Sci fi", "Sci fi"),
        Pair("Seinen", "Seinen"),
        Pair("Shoujo", "Shoujo"),
        Pair("Shoujo ai", "Shoujo ai"),
        Pair("Shounen", "Shounen"),
        Pair("Shounen ai", "Shounen ai"),
        Pair("Slice of life", "Slice of life"),
        Pair("Smut", "Smut"),
        Pair("Sports", "Sports"),
        Pair("Supernatural", "Supernatural"),
        Pair("Tragedy", "Tragedy"),
        Pair("Webtoons", "Webtoons"),
        Pair("Yaoi", "Yaoi"),
        Pair("Yuri", "Yuri"),
    )
}
