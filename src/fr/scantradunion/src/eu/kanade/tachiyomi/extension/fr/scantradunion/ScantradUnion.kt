package eu.kanade.tachiyomi.extension.fr.scantradunion

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ScantradUnion : KeiSource() {

    // ========================= Popular =========================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/projets/").asJsoup()
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

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
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

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val segments = url.pathSegments.filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        val firstSegment = segments[0]
        val mangaUrl = when {
            firstSegment == "manga" || firstSegment == "projets" -> url.toString()
            firstSegment == "read" && segments.size >= 2 -> "$baseUrl/manga/${segments[1]}/"
            segments.size == 1 -> "$baseUrl/manga/$firstSegment/"
            else -> return null
        }

        val document = client.get(mangaUrl).asJsoup()
        if (document.selectFirst(".projet-description") == null) return null

        return document.toSManga().apply {
            setUrlWithoutDomain(document.location())
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query)
            addQueryParameter("asp_active", "1")
            addQueryParameter("p_asid", "1")
            addQueryParameter("p_asp_data", SEARCH_URL_SUFFIX_DATA)
        }.build()

        val document = client.get(url).asJsoup()

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

    // ========================= Details & Chapters =========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(document.toSManga(), document.toChapterList())
    }

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

    private fun Document.toChapterList(): List<SChapter> = select(".links-projects li").map { element ->
        SChapter.create().apply {
            val chapterNumberStr = element.select(".chapter-number").text()
            val dateUploadStr = element.select(".name-chapter").first()?.children()?.getOrNull(2)?.text() ?: ""
            val chapterName = element.select(".chapter-name").text()
            val url = element.select(".btnlel").map { it.attr("href") }
                .firstOrNull { it.startsWith("https://scantrad-union.com/read/") }
                ?: element.select(".btnlel").attr("href") // Fallback
            val chapterNumberStrFormatted = formatMangaNumber(chapterNumberStr)

            name = listOf(chapterNumberStrFormatted, chapterName).filter(String::isNotBlank).joinToString(" - ")
            date_upload = DATE_FORMAT.tryParseDate(dateUploadStr)
            scanlator = element.select(".btnteam").joinToString(" ") { it.text() }
            setUrlWithoutDomain(url)
        }
    }

    // ========================= Pages =========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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

    // ========================= Utils =========================

    private fun formatMangaNumber(value: String): String = value.removePrefix("#").trim()

    private fun formatMangaTitle(value: String): String = value.removePrefix("[Partenaire]").trim()

    private fun mapMangaStatusStringToConst(status: String): Int = when (status.trim().lowercase(Locale.FRENCH)) {
        "en cours" -> SManga.ONGOING
        "terminé" -> SManga.COMPLETED
        "licencié" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val SEARCH_URL_SUFFIX_DATA = "YXNwX2dlbiU1QiU1RD10aXRsZSZjdXN0b21zZXQlNUIlNUQ9bWFuZ2E="

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d-M-yyyy", Locale.FRANCE)
    }
}
