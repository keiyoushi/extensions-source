package eu.kanade.tachiyomi.extension.fr.starboundscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
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
        val document = response.asJsoup()
        return document.select(".chapter-item").mapNotNull { element ->
            val link = element.selectFirst("a.chapter-link")!!
            val href = link.attr("abs:href")

            if (href == "#" || href.isEmpty() || element.text().contains("VIP") || element.text().contains("Réservé")) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                val urlWithSuffix = if (href.contains("?")) {
                    "$href&style=list"
                } else {
                    "$href?style=list"
                }
                setUrlWithoutDomain(urlWithSuffix)

                val rawName = link.selectFirst("h3")!!.text()
                name = rawName.replace("NEW", "").trim()

                val dateText = element.selectFirst(".chapter-meta")!!.text()
                date_upload = customDateFormat.tryParse(dateText)
            }
        }
    }

    private fun Element.imgAttr(): String {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            else -> attr("abs:src")
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.wp-manga-chapter-img").mapIndexed { index, element ->
            val imageUrl = element.imgAttr()
            Page(index, url = document.location(), imageUrl = imageUrl)
        }
    }
}
