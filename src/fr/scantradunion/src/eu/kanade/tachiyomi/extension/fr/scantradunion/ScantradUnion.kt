package eu.kanade.tachiyomi.extension.fr.scantradunion

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ScantradUnion : HttpSource() {

    override val name = "Scantrad Union"

    override val baseUrl = "https://scantrad-union.com"

    override val lang = "fr"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/projets/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".index-top3-a").map { element ->
            SManga.create().apply {
                title = formatMangaTitle(element.select(".index-top3-title").text())
                setUrlWithoutDomain(element.attr("href"))
                thumbnail_url = element.select(".index-top3-bg").attr("style")
                    .substringAfter("background:url('")
                    .substringBefore("')")
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".dernieresmaj .colonne").map { element ->
            SManga.create().apply {
                val titleLink = element.selectFirst("a.text-truncate")!!
                title = formatMangaTitle(titleLink.text())
                thumbnail_url = element.select("img.attachment-thumbnail").attr("src")
                author = element.select(".nomteam").text()
                artist = author
                setUrlWithoutDomain(titleLink.attr("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(baseUrl)) {
            val url = query.toHttpUrlOrNull()
            if (url != null) {
                val segments = url.pathSegments.filter { it.isNotBlank() }
                if (segments.isEmpty()) return latestUpdatesRequest(page)

                val firstSegment = segments[0]
                if (firstSegment == "manga" || firstSegment == "projets") {
                    return GET(query, headers)
                }

                if (firstSegment == "read" && segments.size >= 2) {
                    return GET("$baseUrl/manga/${segments[1]}/", headers)
                }

                if (segments.size == 1) {
                    return GET("$baseUrl/manga/$firstSegment/", headers)
                }
            }
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query)
            addQueryParameter("asp_active", "1")
            addQueryParameter("p_asid", "1")
            addQueryParameter("p_asp_data", SEARCH_URL_SUFFIX_DATA)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (document.selectFirst(".projet-description") != null) {
            val manga = document.toSManga().apply {
                setUrlWithoutDomain(response.request.url.toString())
            }
            return MangasPage(listOf(manga), false)
        }

        val mangas = document.select("article.post-outer").map { element ->
            SManga.create().apply {
                val titleLinkElem = element.selectFirst("a.index-post-header-a")!!
                title = formatMangaTitle(titleLinkElem.text())
                setUrlWithoutDomain(titleLinkElem.attr("href"))
                thumbnail_url = element.select("img.wp-post-image").attr("src")
            }
        }

        return MangasPage(mangas, false)
    }

    // ========================= Details =========================

    override fun mangaDetailsParse(response: Response): SManga = response.asJsoup().toSManga()

    private fun Document.toSManga(): SManga = SManga.create().apply {
        val title = select(".projet-description h2").text()
        val statusStr = select(".label.label-primary").getOrNull(2)?.text() ?: ""

        this.title = formatMangaTitle(title)
        thumbnail_url = select(".projet-image img").attr("src")
        description = select(".sContent").text()
        author = select("div.project-details a[href*=auteur]")
            .joinToString(", ") { it.text() }
        artist = author
        status = mapMangaStatusStringToConst(statusStr)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // ========================= Chapters =========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".links-projects li").map { element ->
            SChapter.create().apply {
                val chapterNumberStr = element.select(".chapter-number").text()
                val dateUploadStr = element.select(".name-chapter").first()?.children()?.getOrNull(2)?.text() ?: ""
                val chapterName = element.select(".chapter-name").text()
                val url = element.select(".btnlel").map { it.attr("href") }
                    .firstOrNull { it.startsWith("https://scantrad-union.com/read/") }
                    ?: element.select(".btnlel").attr("href") // Fallback
                val chapterNumberStrFormatted = formatMangaNumber(chapterNumberStr)

                name = listOf(chapterNumberStrFormatted, chapterName).filter(String::isNotBlank).joinToString(" - ")
                date_upload = parseFrenchDateFromString(dateUploadStr)
                scanlator = element.select(".btnteam").joinToString(" ") { it.text() }
                setUrlWithoutDomain(url)
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ========================= Pages =========================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#webtoon a img")
            .map { imgElem ->
                val imgElemDataSrc = imgElem.attr("data-src")
                val imgElemSrc = imgElem.attr("src")

                if (imgElemDataSrc.isNullOrBlank()) imgElemSrc else imgElemDataSrc
            }
            .distinct()
            .mapIndexed { index, imgUrl ->
                Page(index, "", imgUrl)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Utils =========================

    private fun formatMangaNumber(value: String): String = value.removePrefix("#").trim()

    private fun formatMangaTitle(value: String): String = value.removePrefix("[Partenaire]").trim()

    private fun parseFrenchDateFromString(value: String): Long = try {
        DATE_FORMAT.parse(value)?.time ?: 0L
    } catch (ex: ParseException) {
        0L
    }

    private fun mapMangaStatusStringToConst(status: String): Int = when (status.trim().lowercase(Locale.FRENCH)) {
        "en cours" -> SManga.ONGOING
        "terminé" -> SManga.COMPLETED
        "licencié" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val SEARCH_URL_SUFFIX_DATA = "YXNwX2dlbiU1QiU1RD10aXRsZSZjdXN0b21zZXQlNUIlNUQ9bWFuZ2E="

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("dd-MM-yyyy", Locale.FRANCE)
        }
    }
}
