package eu.kanade.tachiyomi.extension.ar.rocksmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class RocksManga : Madara(
    "Rocks Manga",
    "https://rocks-manga.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
) {

    override fun popularMangaSelector() = ".shido-manga"
    override val popularMangaUrlSelector = "a.s-manga-title"
    override val mangaDetailsSelectorTitle = ".title"
    override val mangaDetailsSelectorAuthor = ".heading:contains(المؤلف:) + .content a"
    override val mangaDetailsSelectorArtist = ".heading:contains(الرسام:) + .content a"
    override val mangaDetailsSelectorStatus = ".status"
    override val mangaDetailsSelectorDescription = ".story"
    override val mangaDetailsSelectorThumbnail = ".profile-manga .poster img"
    override val mangaDetailsSelectorGenre = ".heading:contains(التصنيف:) + .content a"
    override val altNameSelector = ".other-name"
    override fun chapterListSelector() = "#chapter-list li.chapter-item"
    override fun chapterDateSelector() = ".ch-post-time"
    override val pageListParseSelector = ".reading-content img"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val filterNonMangaItems = false

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = super.chapterFromElement(element)
        chapter.name = element.selectFirst(".detail-ch")!!.text()
        return chapter
    }
}
