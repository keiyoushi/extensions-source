package eu.kanade.tachiyomi.extension.ar.rocksmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class RocksManga :
    Madara(
        "Rocks Manga",
        "https://rocksmanga.com",
        "ar",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
    ) {

    override fun popularMangaSelector() = ".unit .inner"
    override val popularMangaUrlSelector = ".info a"
    override fun popularMangaNextPageSelector() = "li.page-item:not(.disabled) > a.page-link[rel=next]"

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override val mangaDetailsSelectorTitle = ".info h1"
    override val mangaDetailsSelectorAuthor = "div.meta span:contains(المؤلف:) + a"
    override val mangaDetailsSelectorArtist = "div.meta span:contains(الرسام:) + a"
    override val mangaDetailsSelectorStatus = ".info p"
    override val mangaDetailsSelectorDescription = "div.description"
    override val mangaDetailsSelectorThumbnail = ".manga-poster img"
    override val mangaDetailsSelectorGenre = "div.meta span:contains(التصنيفات:) ~ a"
    override val altNameSelector = ".info h6"
    override fun chapterListSelector() = "div.list-body-hh ul li"
    override fun chapterDateSelector() = "span.time"
    override val pageListParseSelector = "#ch-images .img"
    override val chapterUrlSuffix = ""

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
    override val fetchGenres = false
    override val filterNonMangaItems = false

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = super.chapterFromElement(element)
        chapter.name = element.selectFirst("zebi")!!.text()
        chapter.scanlator = element.selectFirst(".username span")?.text()
        return chapter
    }
}
