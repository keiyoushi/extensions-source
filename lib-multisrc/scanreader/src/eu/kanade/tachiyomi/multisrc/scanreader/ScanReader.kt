package eu.kanade.tachiyomi.multisrc.scanreader

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
private data class AjaxResponse(val data: String? = null)

abstract class ScanReader(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    private val dateFormat by lazy { SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH) }

    // ====================== Headers ======================

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ====================== Popular ======================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (page > 1) {
            return Observable.fromCallable {
                val response = client.newCall(
                    GET("$baseUrl/bibliotheque/page/${page - 1}/?sort=views", headers),
                ).execute()
                val document = response.asJsoup()
                val mangas = document.select("div.manga-card")
                    .map { mangaFromCard(it) }
                    .filterNot { it.title.contains("(Novel)") }
                val hasNextPage = document.selectFirst("a.pagination-next") != null
                MangasPage(mangas, hasNextPage)
            }
        }
        return super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.request.url != baseUrl.toHttpUrl()) {
            return MangasPage(emptyList(), false)
        }
        val mangas = response.asJsoup()
            .select("div.popular-section div.manga-card")
            .map { mangaFromCard(it) }
            .filterNot { it.title.contains("(Novel)") }
        return MangasPage(mangas, true)
    }

    // ====================== Latest ======================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/dernieres-sorties/page/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.manga-cover")
            .mapNotNull { mangaFromLatestCard(it) }
            .filterNot { it.title.contains("(Novel)") }
        val hasNextPage = document.selectFirst("a.pagination-next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ====================== Search ======================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/?s=${query.trim()}&post_type=manga", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val cards = document.select("div.manga-card")
        val mangas = cards
            .map { mangaFromCard(it) }
            .filterNot { it.title.contains("(Novel)") }
        return MangasPage(mangas, false)
    }

    override fun getFilterList() = FilterList()

    // ====================== Manga Details ======================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.manga-title")!!.text()
            thumbnail_url = document.selectFirst("meta[property='og:image']")?.absUrl("content")
            description = document.selectFirst("div.manga-content div[style*='background: #333'] p")?.text()

            document.select("div.manga-info-grid > div").forEach { row ->
                val label = row.selectFirst("div:first-child")?.text() ?: return@forEach
                val valueEl = row.selectFirst("div:last-child") ?: return@forEach
                when {
                    label.contains("Auteur", ignoreCase = true) ->
                        author = valueEl.text()
                    label.contains("Genres", ignoreCase = true) ->
                        genre = valueEl.select("span").joinToString(", ") { it.text() }
                    label.contains("Statut", ignoreCase = true) -> {
                        val statusText = valueEl.text()
                        status = when {
                            statusText.contains("cours", ignoreCase = true) -> SManga.ONGOING
                            statusText.contains("Terminé", ignoreCase = true) -> SManga.COMPLETED
                            statusText.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
                            statusText.contains("Licencié", ignoreCase = true) -> SManga.LICENSED
                            else -> SManga.UNKNOWN
                        }
                    }
                }
            }
            initialized = true
        }
    }

    // ====================== Chapter List ======================
    // Chapters are loaded via a WordPress admin-ajax.php POST.
    // We extract the nonce and manga ID from the static page, then POST to get the HTML.

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            val document = client.newCall(mangaDetailsRequest(manga)).execute().asJsoup()

            val container = document.selectFirst("#secure-chapters-container")
                ?: return@fromCallable emptyList()

            val mangaId = container.attr("data-manga-id")
            val nonce = container.attr("data-nonce")

            if (mangaId.isBlank() || nonce.isBlank()) return@fromCallable emptyList()

            val ajaxHeaders = headersBuilder()
                .set("Referer", (baseUrl + manga.url).toHttpUrl().toString())
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            val ajaxBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("action", "load_protected_chapters_html")
                .addFormDataPart("manga_id", mangaId)
                .addFormDataPart("nonce", nonce)
                .build()

            val ajaxResponse = client.newCall(
                POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, ajaxBody),
            ).execute()

            chapterListParse(ajaxResponse)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val bodyStr = response.body.string()

        // admin-ajax.php may return raw HTML or a JSON envelope: {"success":true,"data":"<html>"}
        val html = runCatching { bodyStr.parseAs<AjaxResponse>().data }
            .getOrNull() ?: bodyStr

        if (html.trim() == "0" || html.trim() == "-1") return emptyList()

        val document = Jsoup.parse(html)

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
                date_upload = dateFormat.tryParse(h4.nextElementSibling()?.ownText()?.trim())
                this.scanlator = scanlator
            }
        }
    }

    // ====================== Page List ======================
    // Images are obfuscated as a JS array of base64-encoded reversed URLs.
    // Decode: Base64.decode(entry).reversed() = real image URL

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()

        val arrayMatch = Regex(
            """(?:const|let|var)\s+\w+\s*=\s*\[((?:\s*"[A-Za-z0-9+/=]+"(?:\s*,\s*)?)+)\s*]""",
        ).find(body) ?: return emptyList()

        return Regex(""""([A-Za-z0-9+/=]{20,})"""")
            .findAll(arrayMatch.groupValues[1])
            .mapIndexed { index, match ->
                val decoded = String(Base64.decode(match.groupValues[1], Base64.DEFAULT)).reversed()
                Page(index, imageUrl = decoded)
            }
            .toList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ====================== Helpers ======================

    private val onClickCoverRegex = Regex("""addToHistory\(\d+\s*,\s*'[^']*'\s*,\s*'([^']+)'""")

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
        return img.absUrl("src").takeIf { it.isNotEmpty() }
    }

    private fun mangaFromCard(element: Element): SManga {
        val link = element.selectFirst("a")!!
        return SManga.create().apply {
            setUrlWithoutDomain(link.attr("href"))
            title = element.selectFirst("h3")!!.text()
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
}
