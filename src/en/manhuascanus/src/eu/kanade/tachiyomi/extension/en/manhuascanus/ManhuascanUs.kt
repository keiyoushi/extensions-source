package eu.kanade.tachiyomi.extension.en.manhuascanus

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ManhuascanUs : MangaThemesia() {
    override val mangaUrlDirectory = "/manga-list"
    override val datePattern = "dd-MM-yyyy"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(Author) a"

    override fun searchMangaUrl(page: Int, query: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(mangaUrlDirectory.substring(1))
        .addQueryParameter("search", query)
        .addQueryParameter("page", page.toString())
}
