package eu.kanade.tachiyomi.multisrc.oyst

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebViewBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.Normalizer
import kotlin.time.Duration.Companion.seconds

abstract class Oyst : KeiSource() {

    // The reader's endpoints only answer the site's own scripts.
    private val readerHeaders: Headers get() = headersBuilder().set("X-Requested-With", "XMLHttpRequest").build()

    private val anubisLock = Any()

    @Volatile
    private var anubisPassedAt = 0L

    private val codesLock = Mutex()

    // Page codes come 12 at a time and expire within minutes: keep the last batch, like the site's reader.
    private var pageCodes: PageCodes? = null

    // The cookie turns off the site's Safe mode, which hides adult projects from visitors.
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addCookie("oystr_safe_mode_anon" to "0")
        .addInterceptor { passAnubis(it) }
        .rateLimit(permits = 3, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = getCatalogue("", SortFilter(SORT_POPULAR), OriginFilter())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getCatalogue("", SortFilter(SORT_UPDATED), OriginFilter())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getCatalogue(
        query,
        filters.firstInstanceOrNull<SortFilter>() ?: SortFilter(),
        filters.firstInstanceOrNull<OriginFilter>() ?: OriginFilter(),
    )

    // Every project is on this one page; the site sorts and searches it in the browser.
    private suspend fun getCatalogue(query: String, sort: SortFilter, origin: OriginFilter): MangasPage {
        val words = query.normalized().split(' ').filter(String::isNotEmpty)
        val mangas = client.get("$baseUrl/nos-projets/", Fetch.DOCUMENT).asJsoup()
            .select("a.catalog-card[data-title]")
            .filter { card -> origin.matches(card) && words.all { it in card.searchText() } }
            .sortedWith(sort.comparator)
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private fun Element.searchText() = "${attr("data-title")} ${attr("data-type")} ${attr("data-genres")}".normalized()

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = attr("data-title")
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = document.toSManga(manga.url),
            chapters = if (fetchChapters) getChapters(document, mangaUrl) else chapters,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        var projectUrl = url
        var document = client.get(projectUrl, Fetch.DOCUMENT).asJsoup()
        // A chapter page links back to its project.
        document.selectFirst("a.m-chap-bar-btn.center")?.let {
            projectUrl = it.absUrl("href").toHttpUrl()
            document = client.get(projectUrl, Fetch.DOCUMENT).asJsoup()
        }
        if (document.selectFirst(".projet-title") == null) return null
        return document.toSManga(projectUrl.encodedPath)
    }

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        val info = select(".projet-meta-row").associate {
            it.selectFirst(".projet-meta-label")?.text() to it.selectFirst(".projet-meta-value")?.text()
        }
        url = mangaUrl
        title = selectFirst(".projet-title")!!.text()
        thumbnail_url = selectFirst(".projet-cover-img")?.absUrl("src")
        author = info["Auteur(e)"]
        artist = info["Artiste"]
        description = selectFirst(".projet-synopsis-text")?.text()
        genre = select(".projet-tag").joinToString { it.text() }
        status = when (info["Statut"]?.lowercase()) {
            "en cours" -> SManga.ONGOING
            "terminé", "one-shot" -> SManga.COMPLETED
            "en pause" -> SManga.ON_HIATUS
            "abandonné" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // The page lists chapters without links: the reader's API gives them, keyed by each chapter's hash.
    private suspend fun getChapters(document: Document, mangaUrl: String): List<SChapter> {
        val project = document.selectFirst("script:containsData(window.PZL=)")!!.data()
            .substringAfter("window.PZL=").substringBefore(";").parseAs<ProjectDto>()
        val links = client.get(readerUrl("l" to project.id.toString()), readerHeaders, Fetch.XHR.from(mangaUrl))
            .parseAs<ChapterLinksDto>().links
        return document.select("a.projet-chap-item").mapNotNull { item ->
            val link = links[item.attr("data-pq")] ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(link)
                name = item.selectFirst(".projet-chap-num")!!.text()
                chapter_number = item.attr("data-num").toFloatOrNull() ?: -1f
                date_upload = item.attr("data-ts").toLongOrNull()?.times(1000) ?: 0L
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        // Past the first three chapters of a project, the page has no reader for visitors who aren't logged in.
        val reader = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("script:containsData(window.PZ=)")?.data()
            ?.substringAfter("window.PZ=")?.substringBefore(";")?.parseAs<ReaderDto>()
            ?: return emptyList()
        return (0 until reader.pageCount).map { Page(it, url = "$chapterUrl#${reader.chapterId},${reader.ttl}") }
    }

    // Each image is behind a short-lived code that the reader's API hands out.
    override suspend fun getImageUrl(page: Page): String {
        val chapterUrl = page.url.substringBefore('#')
        val (chapterId, ttl) = page.url.substringAfter('#').split(',')
        val code = codesLock.withLock {
            val batch = pageCodes?.takeIf { it.chapterId == chapterId && it.expiresAt > System.currentTimeMillis() && page.index in it.codes }
                ?: fetchPageCodes(chapterUrl, chapterId, page.index, ttl.toInt()).also { pageCodes = it }
            batch.codes.remove(page.index)
        } ?: throw Exception("Page introuvable")
        return "$baseUrl/r/$code"
    }

    private suspend fun fetchPageCodes(chapterUrl: String, chapterId: String, firstPage: Int, ttl: Int, token: String? = null): PageCodes {
        val fetchedAt = System.currentTimeMillis()
        val url = readerUrl("c" to chapterId, "f" to firstPage.toString(), "t" to token)
        val response = client.get(url, readerHeaders, Fetch.XHR.from(chapterUrl), ensureSuccess = false)
        if (response.code == 429) {
            response.close()
            throw Exception("Lecture trop rapide pour le site, réessayez dans une minute")
        }
        val dto = response.parseAs<PageCodesDto>()
        // When pages go faster than a human reads, the site asks for a Turnstile check first.
        if (dto.captchaSiteKey != null && token == null) {
            return fetchPageCodes(chapterUrl, chapterId, firstPage, ttl, getTurnstileToken(chapterUrl, dto.captchaSiteKey))
        }
        val codes = dto.codes.orEmpty().associate { it.page to it.code }.toMutableMap()
        // The site's reader stops using a code 15 seconds before it expires.
        return PageCodes(chapterId, fetchedAt + (ttl - 15) * 1000L, codes)
    }

    override fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(readerHeaders)
        // The site's reader loads images with fetch().
        .fetch(Fetch.XHR.from(page.url.substringBefore('#')))
        .build()

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        OriginFilter(),
    )

    private fun readerUrl(vararg params: Pair<String, String?>): HttpUrl = "$baseUrl/r/".toHttpUrl().newBuilder().apply {
        params.forEach { (name, value) -> value?.let { addQueryParameter(name, it) } }
    }.build()

    /**
     * Chapters and the reader's API are behind Anubis, which a browser passes on its own (a short wait or
     * a proof of work), so a WebView passes it here; its pass is a cookie the app shares with the WebView.
     */
    private fun passAnubis(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestedAt = System.currentTimeMillis()
        val response = chain.proceed(request)
        val checkUrl = response.request.url
        if (!checkUrl.isAnubis()) return response
        response.close()
        synchronized(anubisLock) {
            // Several requests can hit the check at once: pass it once, for all of them.
            if (anubisPassedAt < requestedAt) {
                val appUserAgent = request.header("User-Agent")
                runWebViewBlocking<Unit>(chain.call()) {
                    // The app's user agent, which Cloudflare's cookie is tied to.
                    userAgent = appUserAgent!!
                    // The check page sends the WebView back to the requested page once passed.
                    onPageStarted { url -> if (url.toHttpUrlOrNull()?.isAnubis() == false) resolve(Unit) }
                    loadUrl(checkUrl.toString())
                }
                anubisPassedAt = System.currentTimeMillis()
            }
        }
        return chain.proceed(request)
    }

    private fun HttpUrl.isAnubis() = encodedPath.startsWith("/.within.website/")

    private fun String.normalized() = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private class PageCodes(val chapterId: String, val expiresAt: Long, val codes: MutableMap<Int, String>)

    private companion object {
        val DIACRITICS = Regex("\\p{Mn}+")
    }
}
