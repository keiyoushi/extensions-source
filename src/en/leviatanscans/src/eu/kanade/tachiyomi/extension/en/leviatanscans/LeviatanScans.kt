package eu.kanade.tachiyomi.extension.en.leviatanscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class LeviatanScans : Madara(
    "Leviatan Scans",
    "https://lscomic.com",
    "en",
    dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US),
) {

    override val id = 4055499394183150749

    override val mangaDetailsSelectorDescription = "div.manga-summary"
    override val mangaDetailsSelectorAuthor = "div.manga-authors"

    override val useNewChapterEndpoint: Boolean = true

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"

    override fun popularMangaFromElement(element: Element) =
        replaceRandomUrlPartInManga(super.popularMangaFromElement(element))

    override fun latestUpdatesFromElement(element: Element) =
        replaceRandomUrlPartInManga(super.latestUpdatesFromElement(element))

    override fun searchMangaFromElement(element: Element) =
        replaceRandomUrlPartInManga(super.searchMangaFromElement(element))

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = replaceRandomUrlPartInChapter(super.chapterFromElement(element))

        with(element) {
            selectFirst(chapterUrlSelector)?.let { urlElement ->
                chapter.name = urlElement.ownText()
            }
        }

        return chapter
    }

    private fun replaceRandomUrlPartInManga(manga: SManga): SManga {
        val split = manga.url.split("/")
        manga.url = split.slice(split.indexOf("manga") until split.size).joinToString("/", "/")
        return manga
    }

    private fun replaceRandomUrlPartInChapter(chapter: SChapter): SChapter {
        val split = chapter.url.split("/")
        chapter.url = baseUrl + split.slice(split.indexOf("manga") until split.size).joinToString("/", "/")
        return chapter
    }
}
