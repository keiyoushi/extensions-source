package eu.kanade.tachiyomi.extension.ar.yokai

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class Yokai : ZeistManga() {

    override val supportsChapterFeed = false
    override val preferChapterUpdatedDate = true

    override suspend fun getChapterList(feedUrl: String, doc: Document?) = super.getChapterList(feedUrl, null)
        .map { chapter ->
            val chapterNumberStr: String? = if (
                chapter.name.startsWith("Chapter", ignoreCase = true)
            ) {
                val numberPart = chapter.name.substringAfter("Chapter").trim().substringBefore(" ")
                chapter.name = "الفصل $numberPart"
                numberPart
            } else {
                arabicChapterRegex.find(chapter.name)?.groupValues?.get(1)
            }
            chapterNumberStr?.toFloatOrNull()?.let { chapter.chapter_number = it }
            chapter
        } + doc!!.select("div#download > div.index-list > a")
        .map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                val text = element.text().trim()
                name = text
                chapter_number = text.substringBefore(' ').toFloatOrNull() ?: 1F
            }
        }
        .distinctBy { it.url.substringBefore("?") }

    companion object {
        private val arabicChapterRegex = Regex("""الفصل\s*(\d+(?:\.\d+)?)""")
    }
}
