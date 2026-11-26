package eu.kanade.tachiyomi.extension.en.dorotoons

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

@Source
abstract class DoroToons :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::resignExpiredPage)
        .addInterceptor(::passHumanCheck)
        .addCookie { listOf("verso_cf" to preferences.contentFilter) }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter().apply { state = 1 }))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return getListing(url)
    }

    private suspend fun getListing(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()

        return MangasPage(document.select("main .card:has(> a.card-cover)").map(::parseCard), document.selectFirst(".pager .here + a") != null)
    }

    private fun parseCard(card: Element): SManga = SManga.create().apply {
        val cover = card.selectFirst("a.card-cover")!!
        url = cover.absUrl("href").toHttpUrl().pathSegments[1]
        title = card.selectFirst(".card-body h3")!!.text()
        thumbnail_url = cover.selectFirst("img")?.absUrl("src")
        genre = cover.selectFirst(".cov-kind")?.textOrNull()?.replaceFirstChar(Char::uppercase)
        status = parseStatus(cover.selectFirst(".cov-status")?.text())
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "browse") return super.getMangasByUrl(url, page)
        val listing = url.newBuilder().removeAllQueryParameters("page").apply {
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return getListing(listing)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = slug }

        return parseDetails(manga, client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        return SMangaUpdate(parseDetails(manga, document), parseChapters(document))
    }

    private fun parseDetails(manga: SManga, document: Document): SManga = manga.apply {
        val main = document.selectFirst(".sp-main")!!
        title = main.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".sp-cover img")?.absUrl("src")
        genre = (listOfNotNull(document.fact("Type")) + main.select(".genres a.tag").map { it.text() })
            .distinct().joinToString().ifEmpty { null }
        status = parseStatus(document.fact("Status"))
        val score = main.selectFirst(".sp-stat-score b")?.text()?.removePrefix("★")?.trim()?.toDoubleOrNull()
        val votes = main.selectFirst(".sp-stat-score span")?.text()?.substringBefore(' ')?.replace(",", "")?.toIntOrNull()
        description = MarkdownDescription.Builder()
            .rating(score, max = 5, votes = votes)
            .info(document.fact("Released"), document.fact("New chapters")?.let { "New chapters $it" })
            .description(main.selectFirst("#synopsis"), keepNewlines = true)
            .altTitles(main.select(".aka-list > span").map { it.text() })
            .build()
    }

    private fun Document.fact(name: String) = selectFirst(".sp-facts dt:matchesOwn(^$name$) + dd")?.textOrNull()

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val showLocked = preferences.showLocked

        return document.select("#chapterList .ch-cell > a.ch").mapNotNull { link ->
            val locked = link.hasClass("ch-locked")
            if (locked && !showLocked) return@mapNotNull null
            SChapter.create().apply {
                url = link.absUrl("href").toHttpUrl().pathSegments.drop(1).joinToString("/")
                name = buildString {
                    if (locked) append("🔒 ")
                    append(link.selectFirst(".ch-main > b")!!.text())
                    link.selectFirst(".ch-title")?.textOrNull()?.let { append(" - ").append(it) }
                }
                link.parent()!!.attr("data-no").toFloatOrNull()?.let { chapter_number = it }
                date_upload = parseDate(link.selectFirst(".when")?.text())
            }
        }
    }

    private fun parseDate(text: String?): Long {
        val match = text?.let(DATE_REGEX::matchEntire) ?: return 0L
        val amount = match.groupValues[1].toLong()
        val now = ZonedDateTime.now()
        val date = when (match.groupValues[2]) {
            "m" -> now.minusMinutes(amount)
            "h" -> now.minusHours(amount)
            "d" -> now.minusDays(amount)
            "w" -> now.minusWeeks(amount)
            "mo" -> now.minusMonths(amount)
            else -> now.minusYears(amount)
        }
        return date.toInstant().toEpochMilli()
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select(".sec-head:has(h2:matchesOwn(^More like this$)) + .grid > .card")
        .map(::parseCard)

    override fun getChapterUrl(chapter: SChapter): String = chapterPage(chapter.url)

    private fun chapterPage(chapterUrl: String) = "$baseUrl/series/$chapterUrl"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(pagesUrl(chapter.url), Fetch.XHR.from(getChapterUrl(chapter)))
        .pageUrls()
        .mapIndexed { index, url -> Page(index, imageUrl = "$url#${chapter.url}") }

    private fun pagesUrl(chapterUrl: String): HttpUrl = chapterPage(chapterUrl).toHttpUrl().newBuilder()
        .addQueryParameter("pages", "1")
        .build()

    private fun Response.pageUrls(): List<HttpUrl> {
        if (body.contentType()?.subtype != "json") {
            close()
            throw IOException("Locked chapter: sign in and unlock it in WebView")
        }
        val dataSaver = preferences.dataSaver
        return parseAs<ReaderDto>().pages.map { request.url.resolve(if (dataSaver) it.lite else it.full)!! }
    }

    // Page links expire within the hour; the fragment holds the chapter
    private fun resignExpiredPage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapterUrl = request.url.fragment?.takeIf { request.url.pathSegments[0] == "pg" } ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (response.code != 403) return response
        response.close()

        // Page paths are /pg/<page id>-<expiry>-<quality>-<hash>-<signature>
        val pageId = request.url.pathSegments.last().substringBefore('-')
        val url = resignedPages(chain, chapterUrl).firstOrNull { it.pathSegments.last().substringBefore('-') == pageId }
            ?: throw IOException("Page not found in chapter $chapterUrl")
        return chain.proceed(request.newBuilder().url(url).build())
    }

    // Last re-signed chapter, so its other expired pages need no further request
    private var resigned: ResignedPages? = null

    @Synchronized
    private fun resignedPages(chain: Interceptor.Chain, chapterUrl: String): List<HttpUrl> {
        resigned?.let { if (it.chapterUrl == chapterUrl && it.fetched.elapsedNow() < RESIGNED_TTL) return it.urls }
        val request = chain.request().newBuilder()
            .url(pagesUrl(chapterUrl))
            .fetch(Fetch.XHR.from(chapterPage(chapterUrl)))
            .build()
        val urls = try {
            chain.proceed(request).pageUrls()
        } catch (e: Exception) {
            throw e as? IOException ?: IOException(e)
        }
        resigned = ResignedPages(chapterUrl, TimeSource.Monotonic.markNow(), urls)
        return urls
    }

    private class ResignedPages(val chapterUrl: String, val fetched: TimeSource.Monotonic.ValueTimeMark, val urls: List<HttpUrl>)

    // Checks passed so far, so requests that hit the check together pass it once
    @Volatile
    private var humanChecksPassed = 0

    // The site's human check: a proof of work its page solves before the box is ticked
    private fun passHumanCheck(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val passedBefore = humanChecksPassed
        val response = chain.proceed(request)
        if (response.request.url.encodedPath != HUMAN_PATH) return response

        val passed = synchronized(this) {
            if (humanChecksPassed != passedBefore) return@synchronized null
            submitHumanCheck(chain, response).also { if (it.request.url.encodedPath != HUMAN_PATH) humanChecksPassed++ }
        } ?: response.close().let { chain.proceed(request) }
        if (passed.request.url.encodedPath == HUMAN_PATH) {
            passed.close()
            throw IOException(HUMAN_CHECK_MESSAGE)
        }
        return passed
    }

    // The answer redirects to the page first asked for
    private fun submitHumanCheck(chain: Interceptor.Chain, check: Response): Response {
        val checkUrl = check.request.url
        val document = check.asJsoup()
        val form = document.selectFirst("form#hcForm")
        val challenge = form?.selectFirst("input[name=challenge]")?.attr("value")
        val bits = document.select("script").firstNotNullOfOrNull { BITS_REGEX.find(it.data()) }?.groupValues?.get(1)?.toInt()
        if (form == null || challenge == null || bits == null) throw IOException(HUMAN_CHECK_MESSAGE)

        val started = TimeSource.Monotonic.markNow()
        val nonce = solve(challenge, bits)
        val body = FormBody.Builder()
            .add("next", form.selectFirst("input[name=next]")?.attr("value") ?: "/")
            .add("challenge", challenge)
            .add("nonce", nonce.toString())
            .add("t", started.elapsedNow().inWholeMilliseconds.toString())
            .build()
        val submit = chain.request().newBuilder()
            .url(checkUrl.resolve(form.attr("action")) ?: throw IOException(HUMAN_CHECK_MESSAGE))
            .post(body)
            .fetch(Fetch.DOCUMENT.from(checkUrl.toString()))
            .build()
        return chain.proceed(submit)
    }

    private fun solve(challenge: String, bits: Int): Int {
        val digest = MessageDigest.getInstance("SHA-256")
        val prefix = "$challenge:".toByteArray()
        return (0..Int.MAX_VALUE).first { nonce ->
            digest.update(prefix)
            ByteBuffer.wrap(digest.digest(nonce.toString().toByteArray())).long.countLeadingZeroBits() >= bits
        }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/browse", Fetch.DOCUMENT).asJsoup()
        .select("#gchips .gchip[data-slug]")
        .map { it.ownText() to it.attr("data-slug") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>().orEmpty()

        return FilterList(
            buildList {
                add(SortFilter())
                if (genres.isNotEmpty()) {
                    add(GenreFilter(genres))
                    add(GenreModeFilter())
                }
                add(TypeFilter())
                add(StatusFilter())
                add(DayFilter())
                add(RatingFilter())
                add(LengthFilter())
                add(Filter.Header("Released between (years)"))
                add(YearFilter("From", "from"))
                add(YearFilter("To", "to"))
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_CONTENT_FILTER
            title = "Content filter"
            summary = "%s"
            entries = arrayOf("All", "SFW", "18+")
            entryValues = arrayOf("all", "sfw", "adult")
            setDefaultValue("all")
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "Show early access chapters"
            summary = "Coin chapters, marked 🔒. Unlock them in WebView to read them."
            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DATA_SAVER
            title = "Data saver"
            summary = "The site reader's lighter pages"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private val SharedPreferences.contentFilter get() = getString(PREF_CONTENT_FILTER, "all")!!
    private val SharedPreferences.showLocked get() = getBoolean(PREF_SHOW_LOCKED, false)
    private val SharedPreferences.dataSaver get() = getBoolean(PREF_DATA_SAVER, false)

    companion object {
        private const val HUMAN_PATH = "/__human"
        private const val HUMAN_CHECK_MESSAGE = "Open in WebView to pass the site's check"
        private val BITS_REGEX = Regex("""solve\("[^"]+",\s*(\d+)""")
        private val DATE_REGEX = Regex("""(\d+)(mo|m|h|d|w|y) ago""")
        private val RESIGNED_TTL = 25.minutes
        private const val PREF_CONTENT_FILTER = "pref_content_filter"
        private const val PREF_SHOW_LOCKED = "pref_show_locked"
        private const val PREF_DATA_SAVER = "pref_data_saver"
    }
}
