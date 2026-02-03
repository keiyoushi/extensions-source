package eu.kanade.tachiyomi.extension.id.lumireverse

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class LumireVerse : Madara(
    "LumireVerse",
    "https://lumire.asia",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
) {
    // ============================== Popular ===============================
    override fun popularMangaSelector() = "div.manga-card"
    override val popularMangaUrlSelector = "h3 a"

    // ============================== Latest ================================
    // Menggunakan implementasi default dari Madara (sama dengan popularMangaSelector).

    // ============================== Search ================================
    override fun searchMangaSelector() = "div.manga-card-fixed"
    override val searchMangaUrlSelector = "h3 a"

    // =============================== Details ================================
    override val mangaDetailsSelectorTitle = "h1.text-4xl"
    override val mangaDetailsSelectorDescription = "div#panel-synopsis .prose"
    override val mangaDetailsSelectorThumbnail = "div.lg\\:col-span-3 img.wp-post-image"
    override val mangaDetailsSelectorStatus = "div.flex:has(i.fa-signal) span.font-medium"
    override val seriesTypeSelector = "div.flex:has(i.fa-text-width) span.rounded-md"
    override val mangaDetailsSelectorGenre = "div#panel-synopsis a[href*='/manga-genre/']"

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "ul#lone-ch-list li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            selectFirst(chapterUrlSelector)!!.let { urlElement ->
                chapter.url = urlElement.attr("abs:href")
                chapter.name = urlElement.selectFirst("h3")?.text()
                    ?: urlElement.text()
            }
            chapter.date_upload = parseChapterDate(selectFirst("p")?.text())
        }

        return chapter
    }

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList()
}
