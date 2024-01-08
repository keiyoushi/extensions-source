package eu.kanade.tachiyomi.extension.all.xkcd

import android.net.Uri.encode
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

open class Xkcd(
    final override val baseUrl: String,
    final override val lang: String,
    dateFormat: String = "yyyy-MM-dd",
) : HttpSource() {
    final override val name = "xkcd"

    final override val supportsLatest = false

    protected open val archive = "/archive"

    protected open val creator = "Randall Munroe"

    protected open val synopsis =
        "A webcomic of romance, sarcasm, math and language."

    protected open val interactiveText =
        "To experience the interactive version of this comic, open it in WebView/browser."

    protected open val altTextUrl = LATIN_ALT_TEXT_URL

    protected open val chapterListSelector = "#middleContainer > a"

    protected open val imageSelector = "#comic > img"

    private val dateFormat = SimpleDateFormat(dateFormat, Locale.ROOT)

    protected fun String.timestamp() = dateFormat.parse(this)?.time ?: 0L

    protected fun String.image() = altTextUrl + "&text=" + encode(this)

    protected open fun String.numbered(number: Any) = "$number - $this"

    // TODO: maybe use BreakIterator
    protected fun wordWrap(title: String, altText: String) = buildString {
        title.split(' ').forEachIndexed { i, w ->
            if (i != 0 && i % 7 == 0) append("\n")
            append(w).append(' ')
        }
        append("\n\n")

        var charCount = 0
        altText.replace("\r\n", " ").split(' ').forEach { w ->
            if (charCount > 25) {
                append("\n")
                charCount = 0
            }
            append(w).append(' ')
            charCount += w.length + 1
        }
    }

    final override fun fetchPopularManga(page: Int) =
        SManga.create().apply {
            title = name
            artist = creator
            author = creator
            description = synopsis
            status = SManga.ONGOING
            thumbnail_url = THUMBNAIL_URL
            setUrlWithoutDomain(archive)
        }.let { Observable.just(MangasPage(listOf(it), false))!! }

    final override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        Observable.just(MangasPage(emptyList(), false))!!

    final override fun fetchMangaDetails(manga: SManga) =
        Observable.just(manga.apply { initialized = true })!!

    override fun chapterListParse(response: Response) =
        response.asJsoup().select(chapterListSelector).map {
            SChapter.create().apply {
                url = it.attr("href")
                val number = url.removeSurrounding("/")
                name = it.text().numbered(number)
                chapter_number = number.toFloat()
                date_upload = it.attr("title").timestamp()
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        // if the img tag is empty or has siblings then it is an interactive comic
        val img = response.asJsoup().selectFirst(imageSelector)?.takeIf {
            it.nextElementSibling() == null
        } ?: error(interactiveText)

        // if an HD image is available it'll be the srcset attribute
        val image = when {
            !img.hasAttr("srcset") -> img.attr("abs:src")
            else -> img.attr("abs:srcset").substringBefore(' ')
        }

        // create a text image for the alt text
        val text = wordWrap(img.attr("alt"), img.attr("title"))

        return listOf(Page(0, "", image), Page(1, "", text.image()))
    }

    final override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    final override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    final override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException("Not used")

    final override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    final override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    final override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException("Not used")

    final override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException("Not used")

    final override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException("Not used")

    companion object {
        private const val THUMBNAIL_URL =
            "https://fakeimg.pl/550x780/ffffff/6e7b91/?font=museo&text=xkcd"

        const val LATIN_ALT_TEXT_URL =
            "https://fakeimg.pl/1500x2126/ffffff/000000/?font=museo&font_size=42"

        const val CJK_ALT_TEXT_URL =
            "https://placehold.jp/42/ffffff/000000/1500x2126.png?css=" +
                "%7B%22padding%22%3A%22300px%22%2C%22text-align%22%3A%22left%22%7D"
    }
}
