package eu.kanade.tachiyomi.extension.fr.scantradunion

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ScantradUnion : ParsedHttpSource() {
    override val name = "Scantrad Union"
    override val baseUrl = "https://scantrad-union.com"
    override val lang = "fr"
    override val supportsLatest = true

    // If these parameters are not used, the search results are incomplete.
    private val searchUrlSuffix = "&asp_active=1&p_asid=1&p_asp_data=YXNwX2dlbiU1QiU1RD10aXRsZSZjdXN0b21zZXQlNUIlNUQ9bWFuZ2E="
    private val frenchDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)

    override fun chapterFromElement(element: Element): SChapter {
        val chapterNumberStr = element.select(".chapter-number").text()
        // We don't have a css selector to select the date directly, but we know that it will always
        // be the third child of a .name-chapter.
        val dateUploadStr = element.select(".name-chapter").first()!!.children().elementAt(2).text()
        val chapterName = element.select(".chapter-name").text()
        // The only way to get the chapter url is to check all .btnlel and take the one starting with https://...
        val url = element.select(".btnlel").map { it.attr("href") }.first { it.startsWith("https://scantrad-union.com/read/") }
        val chapterNumberStrFormatted = formatMangaNumber(chapterNumberStr)

        val chapter = SChapter.create()
        // The chapter name is often empty
        // So we will display ${chapterNumber} - ${chapterName} and only ${chapterNumber} if chapterName is empty
        chapter.name = listOf(chapterNumberStrFormatted, chapterName).filter(String::isNotBlank).joinToString(" - ")
        chapter.date_upload = parseFrenchDateFromString(dateUploadStr)
        // The scanlator is several teams of translators concatenated in one string.
        chapter.scanlator = element.select(".btnteam").joinToString(" ") { teamElem -> teamElem.text() }
        chapter.setUrlWithoutDomain(url)
        return chapter
    }

    override fun chapterListSelector(): String = ".links-projects li"

    override fun imageUrlParse(document: Document): String = ""

    override fun latestUpdatesFromElement(element: Element): SManga {
        val title = element.select("a.text-truncate").text()
        val url = element.select("a.text-truncate").attr("href")

        val manga = SManga.create()
        manga.title = formatMangaTitle(title)
        manga.thumbnail_url = element.select("img.attachment-thumbnail").attr("src")
        manga.author = element.select(".nomteam").text()
        // Cannot distinguish authors and artists because they are in the same section.
        manga.artist = manga.author
        manga.setUrlWithoutDomain(url)
        return manga
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = ".dernieresmaj .colonne"

    override fun mangaDetailsParse(document: Document): SManga {
        val title = document.select(".projet-description h2").text()
        val statusStr = document.select(".label.label-primary")[2].text()

        val manga = SManga.create()
        manga.title = formatMangaTitle(title)
        manga.thumbnail_url = document.select(".projet-image img").attr("src")
        manga.description = document.select(".sContent").text()
        manga.author = document.select("div.project-details a[href*=auteur]")
            .joinToString(", ") { teamElem -> teamElem.text() }
        // Cannot distinguish authors and artists because they are in the same section.
        manga.artist = manga.author
        manga.status = mapMangaStatusStringToConst(statusStr)
        manga.setUrlWithoutDomain(document.location())
        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#webtoon a img")
            .map { imgElem: Element ->
                // In webtoon mode, images have an src attribute only.
                // In manga mode, images have a data-src attribute that contains the src
                val imgElemDataSrc = imgElem.attr("data-src")
                val imgElemSrc = imgElem.attr("src")

                if (imgElemDataSrc.isNullOrBlank()) imgElemSrc else imgElemDataSrc
            }
            // Since June 2021, webtoon html has both elements sometimes (data-src and src)
            // So there are duplicates when fetching pages
            // https://github.com/tachiyomiorg/tachiyomi-extensions/issues/7694
            // The distinct() prevent this problem when it happens
            .distinct()
            .mapIndexed { index: Int, imgUrl: String ->
                Page(index, "", imgUrl)
            }
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.title = formatMangaTitle(element.select(".index-top3-title").text())
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.thumbnail_url = element.select(".index-top3-bg").attr("style")
            .substringAfter("background:url('").substringBefore("')")
        return manga
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/projets/", headers)
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaSelector(): String = ".index-top3-a"

    override fun searchMangaFromElement(element: Element): SManga {
        val titleLinkElem = element.select("a.index-post-header-a")

        val manga = SManga.create()
        manga.title = formatMangaTitle(titleLinkElem.text())
        manga.setUrlWithoutDomain(titleLinkElem.attr("href"))
        manga.thumbnail_url = element.select("img.wp-post-image").attr("src")
        return manga
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/?s=$query$searchUrlSuffix"
        return GET(searchUrl, headers)
    }

    override fun searchMangaSelector(): String = "article.post-outer"

    private fun formatMangaNumber(value: String): String {
        return value.removePrefix("#").trim()
    }

    private fun formatMangaTitle(value: String): String {
        // Translations produced by Scantrad Union partners are prefixed with "[Partenaire] ".
        return value.removePrefix("[Partenaire]").trim()
    }

    private fun parseFrenchDateFromString(value: String): Long {
        return try {
            frenchDateFormat.parse(value)?.time ?: 0L
        } catch (ex: ParseException) {
            0L
        }
    }

    private fun mapMangaStatusStringToConst(status: String): Int {
        return when (status.trim().lowercase(Locale.FRENCH)) {
            "en cours" -> SManga.ONGOING
            "terminé" -> SManga.COMPLETED
            "licencié" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }
    }
}
