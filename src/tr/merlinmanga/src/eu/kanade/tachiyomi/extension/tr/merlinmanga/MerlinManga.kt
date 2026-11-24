package eu.kanade.tachiyomi.extension.tr.merlinmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class MerlinManga : Madara() {

    override val chapterMode = ChapterMode.MangaAjax

    override val genreDirectory = "genre"

    override val archiveUrlSelector = ".mm-card__title a"

    // Every series credits the site's own account as author and artist
    override val mangaDetailsSelectorAuthor = "div.author-content > a:not([href$='/merlinmanga/'])"

    override val mangaDetailsSelectorArtist = "div.artist-content > a:not([href$='/merlinmanga/'])"

    override val updatingRegex = UPDATING_REGEX

    // Tags are SEO phrases ("<title> manga oku")
    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        genre = document.select(mangaDetailsSelectorGenre).joinToString { it.text() }.ifEmpty { null }
    }

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = url?.replace(THUMBNAIL_SIZE_REGEX, "")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.encodedPathSegments.filter(String::isNotEmpty)
        if (segments.size < 3 || segments[0] != mangaSubString) return super.getMangaByUrl(url)
        val seriesUrl = url.newBuilder().encodedPath("/${segments[0]}/${segments[1]}/").query(null).build()
        return super.getMangaByUrl(seriesUrl)
    }

    override fun chapterFromElement(element: Element, mangaPath: String): SChapter? = super.chapterFromElement(element, mangaPath)?.apply {
        if (element.hasClass("mp-locked")) name = "🔒 $name"
        date_upload = Instant.tryParse(element.selectFirst("time[datetime]")?.attr("datetime"))
    }

    // The latest chapters of each series are for members (a free account)
    override fun parsePages(document: Document): List<Page> {
        if (document.selectFirst("div.mp-lock") != null) {
            throw Exception("Log in to the site in WebView to read this chapter (a free account is enough)")
        }
        return super.parsePages(document)
    }

    companion object {
        private val UPDATING_REGEX = Regex("Güncelleme")

        // WordPress resizes ("-350x476") of the original cover
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
