package eu.kanade.tachiyomi.extension.all.kdtscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class KdtScans : Madara(
    "KDT Scans",
    "https://kdtscans.com",
    "all",
) {
    override val useNewChapterEndpoint = true
    override val fetchGenres = false

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            title = title.cleanupTitle()
        }
    }

    override fun searchMangaSelector() = "div.c-tabs-item__content:not(:contains([LN]))"

    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            title = title.cleanupTitle()
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            title = title.cleanupTitle()
        }
    }

    private fun String.cleanupTitle() = replace(titleCleanupRegex, "").trim()

    private val titleCleanupRegex =
        Regex("""^\[(ESPAÑOL|English|HD|VIP)\]\s+(–\s+)?""", RegexOption.IGNORE_CASE)

    override fun chapterListSelector() = "li.wp-manga-chapter:not(:has(.required-login))"
}
