package eu.kanade.tachiyomi.extension.ja.mangagun

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class MangaGun : FMReader() {

    override val infoElementSelector = "div.manga-detail-container"
    override val mangaDetailsSelectorDescription = ".description-text-content, .manga-info-list > li:nth-child(1) .info-field-value"

    // The title is base64 encoded and filled in by JS
    override fun parseMangaTitle(document: Document): String? = document.selectFirst("h1.manga-main-title")
        ?.attr("data-enc")
        ?.let { String(Base64.decode(it, Base64.DEFAULT)) }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addCookie("smartlink_shown" to "1")

    // source is picky about URL format
    private fun mangaUrl(sortBy: String, page: Int) = "$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&ungenre=&magazine=&sort=$sortBy&sort_type=DESC"

    override fun popularMangaUrl(page: Int) = mangaUrl("views", page)

    override fun latestUpdatesUrl(page: Int) = mangaUrl("last_update", page)

    override fun popularMangaSelector() = "div.manga-grid div.manga-card"

    override fun popularMangaNextPageSelector() = ".page-link.next"

    override fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector()).first()?.hasAttr("href") ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst(".manga-title")!!.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.text()
        }
        thumbnail_url = getImgAttr(element.selectFirst(".manga-cover"))
    }

    override fun getImgAttr(element: Element?): String? = when {
        element == null -> null

        element.hasAttr("data-original") -> element.attr("abs:data-original")

        element.hasAttr("data-src") -> element.attr("abs:data-src")

        element.hasAttr("data-bg") -> element.attr("abs:data-bg")

        element.hasAttr("data-srcset") -> element.attr("abs:data-srcset")

        element.hasAttr("style") -> element.attr("style").substringAfter("url(")
            .substringBefore(")").trim('\'', '"')

        else -> element.attr("abs:src")
    }

    override suspend fun fetchChapterList(manga: SManga, mangaPage: Document): List<SChapter> {
        val index = manga.url.indexOf("manga-")
        // Handling version compatibility, previous version used the 'raw-' prefix.
        val slug = if (index >= 0) {
            manga.url.substring(index + 6)
        } else {
            manga.url.substringAfter("raw-")
        }.substringBefore(".html")

        return client.get("$baseUrl/app/manga/controllers/cont.Listchapter.php?slug=$slug").asJsoup().select(".at-series a").map {
            SChapter.create().apply {
                name = it.select(".chapter-name").text()
                url = it.attr("abs:href").substringAfter("controllers")
                date_upload = parseChapterDate(it.select(".chapter-time").text())
            }
        }
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[dateValueIndex].toInt()
        val chapterDate = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (date.split(' ')[dateWordIndex]) {
            "mins", "minutes" -> chapterDate.add(Calendar.MINUTE, -value)
            "hours" -> chapterDate.add(Calendar.HOUR_OF_DAY, -value)
            "days" -> chapterDate.add(Calendar.DATE, -value)
            "weeks" -> chapterDate.add(Calendar.DATE, -value * 7)
            "months" -> chapterDate.add(Calendar.MONTH, -value)
            "years" -> chapterDate.add(Calendar.YEAR, -value)
            else -> return 0
        }

        return chapterDate.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("img[id~=page\\d+]")

        return images.mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }
}
