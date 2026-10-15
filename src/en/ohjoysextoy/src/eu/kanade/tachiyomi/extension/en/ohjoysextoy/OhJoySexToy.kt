package eu.kanade.tachiyomi.extension.en.ohjoysextoy

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MULTI_SPACE_REGEX = "\\s{6,}".toRegex()

private val dateFormat = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH)

@Source
abstract class OhJoySexToy : KeiSource() {

    // Browse

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/category/comic/page/$page/").asJsoup()
        val mangas = document.select(".comicthumbwrap").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".comicarchiveframe > a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst(".comicthumbdate")!!.text().substringBefore(" by")
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".pagenav-left a") != null

        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("#MattsRecentComicsBar > ul > div").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".comicarchiveframe > a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst(".comicthumbdate")!!.text().substringBefore(" by")
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, false)
    }

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("h2.post-title").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text().substringBefore(" by")
            }
        }

        return MangasPage(mangas, false)
    }

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            val ogTitle = document.selectFirst("meta[property=\"og:title\"]")!!.attr("content")

            title = ogTitle.substringBefore(" by")
            author = ogTitle.substringAfter("by ", "").takeIf { it.isNotEmpty() }
            description = parseDescription(document)
            genre = document.select("meta[property=\"article:section\"]:not(:first-of-type)")
                .eachAttr("content")
                .joinToString()
            status = SManga.COMPLETED
            thumbnail_url = document.selectFirst("meta[property=\"og:image\"]")?.absUrl("content")
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            setUrlWithoutDomain(document.selectFirst("meta[property=\"og:url\"]")!!.absUrl("content"))
        }

        val chapter = SChapter.create().apply {
            name = document.title()
            scanlator = document.selectFirst(".post-author a")?.text()
            date_upload = dateFormat.tryParseDate(document.selectFirst(".post-date")?.text())
            setUrlWithoutDomain(document.location())
        }

        return SMangaUpdate(details, listOf(chapter))
    }

    private fun parseDescription(document: Document): String = buildString {
        val desc = document.selectFirst("meta[property=\"og:description\"]")
            ?.attr("content")
            ?.split(MULTI_SPACE_REGEX)
            ?.firstOrNull()

        if (!desc.isNullOrEmpty()) {
            append(desc)
            append("...\n\n")
        }

        val authorLinks = document.select(".entry div.ui-tabs div a")
        if (authorLinks.isNotEmpty()) {
            val authorCredits = authorLinks.joinToString("\n") { link ->
                "${link.text()}: ${link.absUrl("href")}"
            }
            append(authorCredits)
            append("\n\n")
        }

        append("(Full description and credits in WebView)")
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.comicpane img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }
}
