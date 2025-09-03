package eu.kanade.tachiyomi.extension.fr.starboundscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class StarboundScans : Madara(
    "Starbound Scans",
    "https://starboundscans.com",
    "fr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH),
) {
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val versionId = 2

    override val client = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val mangaDetailsSelectorTitle = "h1"
    override val mangaDetailsSelectorThumbnail = ".project-cover"
    override val mangaDetailsSelectorDescription = ".card-body .black-orion-article-content p"
    override val mangaDetailsSelectorStatus = ".info-item:contains(Statut) .info-value"
    override val mangaDetailsSelectorAuthor = ".info-item:contains(Auteur) a"
    override val mangaDetailsSelectorArtist = ".info-item:contains(Auteur) a"
    override val mangaDetailsSelectorGenre = ".info-item:contains(Genres) .genre-tag"

    private val customDateFormat = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.body.string().let { org.jsoup.Jsoup.parse(it) }
        return document.select(".chapter-item").mapNotNull { element ->
            try {
                chapterFromElement(element)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        val link = element.selectFirst("a.chapter-link")
        val href = link?.attr("abs:href") ?: ""

        if (href == "#" || href.isEmpty() || element.text().contains("VIP") || element.text().contains("Réservé")) {
            throw Exception("VIP chapter - skip")
        }

        chapter.setUrlWithoutDomain(href)

        val rawName = link?.select("h3")?.text()?.trim() ?: "Chapitre inconnu"
        chapter.name = rawName.replace("NEW", "").trim()

        val dateText = element.select(".chapter-meta").text().trim()
        chapter.date_upload = customDateFormat.tryParse(dateText)

        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.wp-manga-chapter-img").mapIndexed { index, element ->
            val imageUrl = element.attr("abs:src")
            Page(index, "", imageUrl)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page) = GET(page.imageUrl ?: "", headers)
}
