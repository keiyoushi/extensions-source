package eu.kanade.tachiyomi.extension.id.yubikiri

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Kaguya :
    Madara(
        "Kaguya",
        "https://kaguya.id",
        "id",
        dateFormat = SimpleDateFormat("d MMMM", Locale("en")),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    override val id = 1557304490417397104

    override val mangaSubString = "all-series"

    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div"
    override val mangaDetailsSelectorThumbnail = "head meta[property='og:image']" // Same as browse

    override fun imageFromElement(element: Element): String? {
        return super.imageFromElement(element)
            ?.takeIf { it.isNotEmpty() }
            ?: element.attr("content") // Thumbnail from <head>
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable { fetchAllChapters(manga) }

    private fun fetchAllChapters(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            val response = client.newCall(POST("${getMangaUrl(manga)}ajax/chapters?t=${page++}", xhrHeaders)).execute()
            val document = response.asJsoup()
            val currentPage = document.select(chapterListSelector())
                .map(::chapterFromElement)

            chapters += currentPage
            response.close()

            if (currentPage.isEmpty()) {
                return chapters
            }
        }
    }

    // ============================== Filter ==============================
    override fun parseGenres(document: Document): List<Genre> = document.select("a.btn[href*=\"genre=\"], a.dropdown-item[href*=\"genre=\"]")
        .map { a ->
            Genre(
                a.text(),
                a.attr("href").substringAfter("genre=").substringBefore("&"),
            )
        }.distinctBy { it.id }

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
