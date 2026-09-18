package eu.kanade.tachiyomi.multisrc.scanreader

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
private class AjaxResponse(val data: String? = null)

abstract class ScanReader : KeiSource() {

    // ====================== Popular ======================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = if (page > 1) {
            val url = if (page > 2) {
                "$baseUrl/bibliotheque/page/${page - 1}/?sort=views"
            } else {
                "$baseUrl/bibliotheque/?sort=views"
            }
            client.get(url).asJsoup()
        } else {
            client.get(baseUrl).asJsoup()
        }

        val mangas = if (page > 1) {
            document.select("div.manga-card")
                .mapNotNull { mangaFromCard(it) }
                .filterNot { it.title.contains("(Novel)") }
        } else {
            document.select("div.popular-section div.manga-card")
                .mapNotNull { mangaFromCard(it) }
                .filterNot { it.title.contains("(Novel)") }
        }

        val hasNextPage = if (page > 1) {
            document.selectFirst("a.pagination-next") != null
        } else {
            mangas.isNotEmpty()
        }

        return MangasPage(mangas, hasNextPage)
    }

    // ====================== Latest ======================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page > 1) "$baseUrl/dernieres-sorties/page/$page/" else "$baseUrl/dernieres-sorties/"
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.manga-cover")
            .mapNotNull { mangaFromLatestCard(it) }
            .filterNot { it.title.contains("(Novel)") }
        val hasNextPage = document.selectFirst("a.pagination-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ====================== Search ======================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query.trim())
            .addQueryParameter("post_type", "manga")
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.manga-card")
            .mapNotNull { mangaFromCard(it) }
            .filterNot { it.title.contains("(Novel)") }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "mangas" || url.pathSegments.getOrNull(1).isNullOrBlank()) return null

        val manga = SManga.create().apply {
            this.url = "/mangas/${url.pathSegments[1]}/"
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ====================== Manga Details & Chapters ======================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl).asJsoup()

        parseMangaDetails(document, manga)

        val chapterList = if (fetchChapters) {
            fetchChapterList(document, mangaUrl)
        } else {
            chapters
        }

        return SMangaUpdate(manga, chapterList)
    }

    private fun parseMangaDetails(document: Document, manga: SManga) {
        manga.apply {
            document.selectFirst("h1.manga-title")?.text()?.let { title = it }
            thumbnail_url = document.selectFirst("meta[property='og:image']")?.absUrl("content")?.takeIf { it.isNotEmpty() }
                ?: extractLazySrc(document.selectFirst("img.wp-post-image"))
                ?: thumbnail_url
            description = document.selectFirst("div.manga-content div[style*='background: #333'] p")?.text()

            document.select("div.manga-info-grid > div").forEach { row ->
                val label = row.selectFirst("div:first-child")?.text()?.lowercase(Locale.FRENCH) ?: return@forEach
                val valueEl = row.selectFirst("div:last-child") ?: return@forEach
                when {
                    label.contains("auteur") ->
                        author = valueEl.text()
                    label.contains("genres") ->
                        genre = valueEl.select("span").joinToString { it.text() }
                    label.contains("statut") -> {
                        val statusText = valueEl.text().lowercase(Locale.FRENCH)
                        status = when {
                            statusText.contains("cours") -> SManga.ONGOING
                            statusText.contains("terminé") -> SManga.COMPLETED
                            statusText.contains("hiatus") -> SManga.ON_HIATUS
                            statusText.contains("licencié") -> SManga.LICENSED
                            else -> SManga.UNKNOWN
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchChapterList(document: Document, mangaUrl: String): List<SChapter> {
        val container = document.selectFirst("#secure-chapters-container")
            ?: return emptyList()

        val mangaId = container.attr("data-manga-id")
        val nonce = container.attr("data-nonce")

        if (mangaId.isBlank() || nonce.isBlank()) return emptyList()

        val ajaxHeaders = headersBuilder()
            .set("Referer", mangaUrl)
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val ajaxBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", "load_protected_chapters_html")
            .addFormDataPart("manga_id", mangaId)
            .addFormDataPart("nonce", nonce)
            .build()

        val response = client.post(
            "$baseUrl/wp-admin/admin-ajax.php",
            ajaxHeaders,
            ajaxBody,
        )

        return parseChapterList(response)
    }

    private fun parseChapterList(response: Response): List<SChapter> {
        val bodyStr = response.body.string()

        // admin-ajax.php may return raw HTML or a JSON envelope: {"success":true,"data":"<html>"}
        val html = runCatching { bodyStr.parseAs<AjaxResponse>().data }
            .getOrNull() ?: bodyStr

        if (html.trim() == "0" || html.trim() == "-1") return emptyList()

        val document = Jsoup.parseBodyFragment(html, baseUrl)

        // Jsoup's HTML5 parser closes <a> before block-level children, so <h4> ends up as a
        // sibling of <a> rather than a descendant. We anchor on <h4> and walk up to find the link.
        return document.select("h4").mapNotNull { h4 ->
            val href = h4.parents()
                .firstNotNullOfOrNull { it.selectFirst("a[href*='/chapitre/']")?.absUrl("href") }
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            val scanlator = h4.parents().firstNotNullOfOrNull { ancestor ->
                ancestor.selectFirst("a[title*=Team]")?.text()
                    ?: ancestor.selectFirst("div:containsOwn(Solo)")?.previousElementSibling()?.text()
            }

            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = h4.text()
                date_upload = dateFormatter.tryParseDate(h4.nextElementSibling()?.ownText(), zone = parisZone)
                this.scanlator = scanlator
            }
        }
    }

    // ====================== Page List ======================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = client.get(baseUrl + chapter.url).body.string()

        val arrayMatch = imageArrayRegex.find(body) ?: return emptyList()

        return imageItemRegex
            .findAll(arrayMatch.groupValues[1])
            .mapIndexed { index, match ->
                val decoded = String(Base64.decode(match.groupValues[1], Base64.DEFAULT)).reversed()
                Page(index, imageUrl = decoded)
            }
            .toList()
    }

    // ====================== Helpers ======================

    private fun extractCoverFromOnClick(onclick: String?): String? {
        if (onclick.isNullOrBlank()) return null
        return onClickCoverRegex.find(onclick)?.groupValues?.get(1)
    }

    private fun extractLazySrc(img: Element?): String? {
        img ?: return null
        img.absUrl("data-lazy-src").takeIf { it.isNotEmpty() }?.let { return it }
        img.attr("data-lazy-srcset").takeIf { it.isNotEmpty() }?.let { srcset ->
            return srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
        }
        return img.absUrl("src").takeIf { it.isNotEmpty() && !it.startsWith("data:") }
    }

    private fun mangaFromCard(element: Element): SManga? {
        val link = element.selectFirst("a") ?: return null
        val title = element.selectFirst("h3")?.text() ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.attr("href"))
            this.title = title
            thumbnail_url = extractCoverFromOnClick(link.attr("onclick"))
                ?: extractLazySrc(element.selectFirst("img"))
        }
    }

    private fun mangaFromLatestCard(cover: Element): SManga? {
        val url = cover.selectFirst("a")?.absUrl("href")?.takeIf { it.isNotEmpty() } ?: return null
        val title = cover.nextElementSibling()?.selectFirst("h3.manga-title-display")?.text() ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(url)
            this.title = title
            thumbnail_url = extractLazySrc(cover.selectFirst("img"))
        }
    }

    companion object {
        private val onClickCoverRegex = Regex("""addToHistory\(\d+\s*,\s*'[^']*'\s*,\s*'([^']+)'""")
        private val imageArrayRegex = Regex("""(?:const|let|var)\s+\w+\s*=\s*\[((?:\s*"[A-Za-z0-9+/=]+"(?:\s*,\s*)?)+)\s*]""")
        private val imageItemRegex = Regex(""""([A-Za-z0-9+/=]{20,})"""")
        private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)
        private val parisZone = ZoneId.of("Europe/Paris")
    }
}
