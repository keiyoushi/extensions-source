package eu.kanade.tachiyomi.extension.ko.manga24

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Manga24 : Madara() {

    override val chapterMode = ChapterMode.MangaAjax

    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d, yyyy", Locale.KOREAN)

    // The site lists chapters oldest first
    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> = super.parseChapterList(document, mangaPath).reversed()

    // Cards and covers show resized copies (name-WxH.ext) of the original image
    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = url?.replace(THUMBNAIL_SIZE_REGEX, "")

    companion object {
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
