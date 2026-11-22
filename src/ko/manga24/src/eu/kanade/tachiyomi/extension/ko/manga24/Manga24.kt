package eu.kanade.tachiyomi.extension.ko.manga24

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Manga24 : Madara() {

    override val chapterMode = ChapterMode.MangaAjax

    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d, yyyy", Locale.KOREAN)

    // ?p=<id> leads to the home page here, so a chapter link goes to its series by its path
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.encodedPathSegments.filter(String::isNotEmpty)
        if (segments.size < 3 || segments[0] != mangaSubString) return super.getMangaByUrl(url)
        return super.getMangaByUrl(url.newBuilder().encodedPath("/${segments[0]}/${segments[1]}/").query(null).build())
    }

    // The site lists chapters oldest first
    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> = super.parseChapterList(document, mangaPath).reversed()

    // Cards and covers show resized copies (name-WxH.ext) of the original image
    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = url?.replace(THUMBNAIL_SIZE_REGEX, "")

    companion object {
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
