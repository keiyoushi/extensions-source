package eu.kanade.tachiyomi.extension.ar.mangalionz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLionz : Madara(
    "MangaLionz",
    "https://manga-lionz.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            selectFirst(popularMangaUrlSelector)!!.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }

            selectFirst("img")?.let {
                manga.thumbnail_url = imageFromElement(it)?.replace("mangalionz", "mangalek")
            }
        }

        return manga
    }

    override val chapterUrlSuffix = ""
}
