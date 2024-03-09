package eu.kanade.tachiyomi.extension.id.mangkomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class SirenKomik : MangaThemesia(
    "Siren Komik",
    "https://sirenkomik.my.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {
    override val id = 8457447675410081142

    override val hasProjectPage = true

    override val seriesTitleSelector = "h1.judul-komik"
    override val seriesThumbnailSelector = ".gambar-kecil img"
    override val seriesGenreSelector = ".genre-komik a"
    override val seriesAuthorSelector = ".keterangan-komik:contains(author) span"
    override val seriesArtistSelector = ".keterangan-komik:contains(artist) span"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".nomer-chapter").text().ifBlank { urlElements.first()!!.text() }
        date_upload = element.selectFirst(".tgl-chapter")?.text().parseChapterDate()
    }

    override fun pageListParse(document: Document): List<Page> {
        // Get external JS for image urls
        val scriptEl = document.selectFirst("script[data-minify]")
        val scriptUrl = scriptEl?.attr("src")
        if (scriptUrl.isNullOrEmpty()) {
            return super.pageListParse(document)
        }

        val scriptResponse = client.newCall(
            GET(scriptUrl, headers),
        ).execute()

        // Inject external JS
        scriptEl.text(scriptResponse.body.string())
        return super.pageListParse(document)
    }
}
