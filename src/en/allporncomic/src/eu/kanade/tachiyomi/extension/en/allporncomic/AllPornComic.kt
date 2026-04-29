package eu.kanade.tachiyomi.extension.en.allporncomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Element

class AllPornComic : Madara("AllPornComic", "https://allporncomic.com", "en") {
    override val mangaSubString = "porncomic"

    // Related Manga
    override fun relatedMangaSelector() = ".crp_related a.crp_link"

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val document = response.asJsoup()
        return document.select(relatedMangaSelector())
            .mapNotNull { manga ->
                SManga.create().apply {
                    setUrlWithoutDomain(manga.attr("abs:href"))
                    manga.selectFirst(".crp_title")?.let { it: Element ->
                        title = it.ownText()
                    } ?: return@mapNotNull null
                    manga.selectFirst("img")?.let { it: Element ->
                        thumbnail_url = processThumbnail(imageFromElement(it), true)
                    }
                }
            }
    }
}
