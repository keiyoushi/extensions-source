package eu.kanade.tachiyomi.extension.tr.mangagezgini

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaGezgini : Madara(
    "MangaGezgini",
    "https://mangagezgini.com",
    "tr",
    SimpleDateFormat("dd/MM/yyy", Locale("tr")),
) {
    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        with(element) {
            select(chapterUrlSelector).first()?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }

                chapter.name = element.select("li.wp-manga-chapter.has-thumb a").text()
            }

            chapter.date_upload = select("img:not(.thumb)").firstOrNull()?.attr("alt")?.let { parseRelativeDate(it) }
                ?: select("span a").firstOrNull()?.attr("title")?.let { parseRelativeDate(it) }
                    ?: parseChapterDate(select(chapterDateSelector()).firstOrNull()?.text())
        }

        return chapter
    }
}
