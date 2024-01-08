package eu.kanade.tachiyomi.extension.en.mangabat

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class Mangabat : MangaBox("Mangabat", "https://m.mangabat.com", "en", SimpleDateFormat("MMM dd,yy", Locale.ENGLISH)) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list-all/$page?type=topview", headers)
    override fun popularMangaSelector() = "div.list-story-item"
    override val latestUrlPath = "manga-list-all/"
    override fun searchMangaSelector() = "div.list-story-item"
    override fun getAdvancedGenreFilters(): List<AdvGenre> = getGenreFilters()
        .drop(1)
        .map { AdvGenre(it.first, it.second) }
}
