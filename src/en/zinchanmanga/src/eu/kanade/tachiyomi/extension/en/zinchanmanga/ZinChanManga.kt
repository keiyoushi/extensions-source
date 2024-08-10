package eu.kanade.tachiyomi.extension.en.zinchanmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ZinChanManga : Madara("ZinChanManga", "https://zinchanmanga.net", "en") {
    override val versionId = 2
    override val useNewChapterEndpoint = true

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            title = url.urlToTitle() ?: title
        }
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            title = url.urlToTitle() ?: title
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            title = document.location().urlToTitle() ?: title
        }
    }

    private fun String.urlToTitle(): String? {
        val slug = substringAfter("/manga/", "")
            .substringBefore("/")
            .takeUnless { it.isBlank() }
            ?: return null

        val result = StringBuilder(slug.length)
        var capitalize = true
        for (char in slug) {
            result.append(
                if (char == '-') {
                    ' '
                } else if (capitalize) {
                    char.uppercase()
                } else {
                    char.lowercase()
                },
            )
            capitalize = char == '-'
        }
        return result.toString()
    }
}
