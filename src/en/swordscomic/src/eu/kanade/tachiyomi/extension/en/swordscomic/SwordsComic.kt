package eu.kanade.tachiyomi.extension.en.swordscomic

import android.net.Uri
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class SwordsComic : HttpSource() {

    override val name = "Swords Comic"

    override val baseUrl = "https://swordscomic.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private fun createManga(): SManga {
        return SManga.create().apply {
            title = "Swords Comic"
            url = "/archive/pages/"
            author = "Matthew Wills"
            artist = author
            description = "A webcomic about swords and the heroes who wield them"
            thumbnail_url = "https://swordscomic.com/media/ArgoksEdgeEmote.png"
        }
    }

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(createManga()), false))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(createManga().apply { initialized = true })
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().select("a.archive-tile")
            .map { element ->
                SChapter.create().apply {
                    name = element.select("strong").text()
                    setUrlWithoutDomain(element.attr("href"))
                    date_upload = element.select("small").text()
                        .let { SimpleDateFormat("dd MMM yyyy", Locale.US).parse(it)?.time ?: 0L }
                }
            }
            .reversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val imageElement = response.asJsoup().select("img#comic-image")
        if (!imageElement.hasAttr("title")) {
            return listOf(Page(0, "", imageElement.attr("abs:src")))
        }

        val builder = StringBuilder()
        var charCount = 0

        for (word in imageElement.attr("title").splitToSequence(" ")) {
            if (charCount + word.length > 45) {
                builder.append("%0A")
                charCount = 0
            }
            charCount += word.length + 1
            builder.append(Uri.encode(word.uppercase()))
            builder.append("+")
        }

        return listOf(Page(0, "", imageElement.attr("abs:src")), Page(1, "", "https://fakeimg.pl/1800x2252/978B65/000000/?text=$builder&font_size=60&font=comic+sans"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
