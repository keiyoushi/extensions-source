package eu.kanade.tachiyomi.multisrc.pam

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.i18n.Intl
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

abstract class Pam :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy {
        if (contains(OLD_HIDE_PREMIUM_PREF)) edit().remove(OLD_HIDE_PREMIUM_PREF).apply()
    }

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "fr"),
        classLoader = this::class.java.classLoader!!,
    )

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(::pageInterceptor)
    }

    private val siteView = SiteView(baseUrl) { headers["User-Agent"]!! }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", popularFilters)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", latestFilters)

    protected abstract val popularFilters: FilterList
    protected abstract val latestFilters: FilterList

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("library")
            if (query.isNotBlank()) addQueryParameter("search", query.trim())

            filters.filterIsInstance<TriStateGroupFilter>().forEach { group ->
                val kind = if (group is TypeGroupFilter) "types" else "genres"
                group.included.takeIf { it.isNotEmpty() }?.also { addQueryParameter("include_$kind", it.joinToString(",")) }
                group.excluded.takeIf { it.isNotEmpty() }?.also { addQueryParameter("exclude_$kind", it.joinToString(",")) }
            }
            filters.firstInstanceOrNull<CheckBoxGroup>()?.also { status ->
                if (status.checked.isNotEmpty()) addQueryParameter("status", status.checked.joinToString(","))
            }
            filters.filterIsInstance<NumberFilter>().forEach { number ->
                number.value?.also { addQueryParameter(number.parameter, it.toString()) }
            }
            filters.firstInstanceOrNull<SortFilter>()?.also { sort ->
                addQueryParameter("orderby", sort.sort)
                if (sort.ascending) addQueryParameter("order", "asc")
            }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        val library = siteView.drive(url.toString(), "(${siteView.asset("library.js")})($SETTLE_MS)") { _, result ->
            Step.Done(result.parseAs<LibraryPage>())
        }
        return MangasPage(
            mangas = library.items.map { it.toSManga(COVER_FRAGMENT) },
            hasNextPage = library.hasNextPage,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/serie/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "serie") return null
        val manga = SManga.create().apply { this.url = segments[1] }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = readSerie(manga, chapters, fetchChapters)

    /** Reads the series page: its details from both tabs, then each page of its chapter list through the site's pager. */
    private suspend fun readSerie(manga: SManga, known: List<SChapter>, withChapters: Boolean): SMangaUpdate {
        var synopsis: String? = null
        val rows = linkedMapOf<String, SeriePage.Row>()
        var lastFirst: String? = null
        var tappedFor = 0

        val serie = siteView.drive(getMangaUrl(manga), "(${siteView.asset("serie.js")})()") { _, result ->
            val page = result.parseAs<SeriePage>()
            if (page.synopsis != null) synopsis = page.synopsis
            // Status, year and alternative titles are on the Information tab
            page.infoTab?.let { return@drive touch(page, it) }
            if (page.info == null || !withChapters) return@drive if (page.info != null) Step.Done(page) else Step.Wait
            if (page.chapterCount == 0) return@drive Step.Done(page)
            val first = page.chapters.firstOrNull()?.path ?: return@drive Step.Wait
            if (first != lastFirst && page.page > tappedFor) {
                lastFirst = first
                tappedFor = page.page
                page.chapters.forEach { rows[it.path] = it }
            }
            when {
                page.next != null && tappedFor == page.page -> touch(page, page.next)
                page.next == null && tappedFor == page.page -> Step.Done(page)
                else -> Step.Wait
            }
        }

        val details = SManga.create().apply {
            url = manga.url
            title = serie.title
            thumbnail_url = serie.cover?.let { "$it#$COVER_FRAGMENT" }
            val info = serie.info!!
            author = info.author.joinToString().ifEmpty { null }
            artist = info.artist.joinToString().ifEmpty { null }
            genre = (serie.types + info.genres).distinct().joinToString().ifEmpty { null }
            status = when (info.status) {
                "ongoing", "upcoming" -> SManga.ONGOING
                "finished" -> SManga.COMPLETED
                "dropped" -> SManga.CANCELLED
                "onhold" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            description = MarkdownDescription.Builder().apply {
                rating(serie.rating?.substringBefore("/")?.toFloatOrNull())
                info(info.year?.let { "${intl["release_year"]}: $it" })
                description(synopsis)
                field(intl["translated_by"], serie.teams)
                altTitles(info.altTitles, intl["alternative_names"])
            }.build()
        }
        if (!withChapters) return SMangaUpdate(details, known)

        // Ages are rounded down to days, weeks, months or years, so a chapter keeps the date it was first stored with
        val stored = known.associate { it.url to it.date_upload }
        val now = System.currentTimeMillis()
        val showPremium = preferences.getBoolean(SHOW_PREMIUM_PREF, false)
        val chapters = rows.values
            .filter { showPremium || !it.premium }
            .map { row ->
                SChapter.create().apply {
                    url = row.path
                    name = buildString {
                        if (row.premium || row.account) append("🔒 ")
                        append(row.name)
                        row.title?.takeIf { it != row.name }?.let { append(" - ", it) }
                    }
                    date_upload = stored[row.path]?.takeIf { it > 0 } ?: row.age?.let { ageMillis(it) }?.let { now - it } ?: 0L
                }
            }
        return SMangaUpdate(details, chapters)
    }

    /** Taps [point] once nothing covers it, scrolling it to the upper part of the screen first. */
    private fun WebViewScope<SeriePage>.touch(page: SeriePage, point: Point): Step<SeriePage> {
        val height = page.viewport.height
        return Step.Act { done ->
            if (point.clear) {
                tap(page.scale, point.x, point.y)
                later(TAP_WAIT_MS, done)
            } else {
                val travel = (point.y - height * 0.3f).coerceIn(-height * 0.7f, height * 0.7f)
                val from = if (travel > 0) height * 0.85f else height * 0.15f
                swipe(page.scale, page.viewport.width * 0.2f, from, page.viewport.width * 0.2f, from - travel)
                later(SWIPE_WAIT_MS, done)
            }
        }
    }

    /** The age the site's chapter list shows, in any of its languages, as milliseconds. */
    private fun ageMillis(age: String): Long? {
        if (age in NOW_WORDS) return 0
        if (age in YESTERDAY_WORDS) return 1.days.inWholeMilliseconds
        val match = AGE_REGEX.matchEntire(age) ?: return null
        val count = match.groupValues[1].toLong()
        val unit = AGE_UNITS[match.groupValues[2]] ?: return null
        return count * unit.inWholeMilliseconds
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val capture = capture(getChapterUrl(chapter).toHttpUrl().encodedPath, 0)
        val count = capture.pageCount.await()
        return List(count) { index ->
            Page(index, imageUrl = "${getChapterUrl(chapter)}#$PAGE_FRAGMENT$index")
        }
    }

    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val captures = linkedMapOf<String, ChapterCapture>()

    /** The chapter's open reader that can still deliver page [index], or a new one starting there. */
    @Synchronized
    private fun capture(chapterPath: String, index: Int): ChapterCapture {
        captures[chapterPath]?.takeIf { it.covers(index) }?.let { return it }
        captures.remove(chapterPath)?.close()
        while (captures.size >= MAX_CAPTURES) captures.remove(captures.keys.first())?.close()
        val dir = File(applicationContext.cacheDir, "pam-pages/${chapterPath.hashCode().toUInt()}-$index")
        return ChapterCapture("$baseUrl$chapterPath", index, siteView, dir, captureScope).also { captures[chapterPath] = it }
    }

    private val covers = Covers(baseUrl, File(applicationContext.cacheDir, "pam-covers/$id"), captureScope) { headers["User-Agent"]!! }

    /** Pages and covers come from the WebViews, never from the network. */
    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.fragment == COVER_FRAGMENT) {
            val url = request.url.newBuilder().fragment(null).build().toString()
            val cover = awaitFile { covers.get(url).await() }
            return fileResponse(request, cover.file, cover.type)
        }
        val index = request.url.fragment?.takeIf { it.startsWith(PAGE_FRAGMENT) }?.removePrefix(PAGE_FRAGMENT)?.toIntOrNull()
            ?: return chain.proceed(request)
        val file = awaitFile { capture(request.url.encodedPath, index).page(index).await() }
        return fileResponse(request, file, "image/jpeg")
    }

    private fun <T> awaitFile(block: suspend () -> T): T = try {
        runBlocking { withTimeout(PAGE_TIMEOUT) { block() } }
    } catch (e: Throwable) {
        throw e as? IOException ?: IOException(e.message ?: "The site's page did not load", e)
    }

    private fun fileResponse(request: Request, file: File, type: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(file.source().buffer().asResponseBody(type.toMediaType(), file.length()))
        .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PREMIUM_PREF
            title = intl["pref_show_premium_title"]
            summary = intl["pref_show_premium_summary"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }
}

private const val SETTLE_MS = 4000
private const val PAGE_FRAGMENT = "pam-page-"
private const val COVER_FRAGMENT = "pam-cover"
private const val MAX_CAPTURES = 2
private val PAGE_TIMEOUT = 5.minutes
private const val TAP_WAIT_MS = 1200L
private const val SWIPE_WAIT_MS = 900L
private const val SHOW_PREMIUM_PREF = "pref_show_premium_chapters"
private const val OLD_HIDE_PREMIUM_PREF = "pref_hide_premium_chapters"

// The site's age formats, in the device's language: en "3 d", fr "3 j / 1 sem.", es "1 mes", de "3 T."; " min" and " h" in all
private val AGE_REGEX = Regex("""(\d+) (\S+)""")
private val NOW_WORDS = setOf("just now", "à l’instant", "ahora", "gerade eben")
private val YESTERDAY_WORDS = setOf("yesterday", "hier", "ayer", "gestern")
private val AGE_UNITS = mapOf(
    "min" to 1.minutes,
    "h" to 1.hours,
    "d" to 1.days, "j" to 1.days, "T." to 1.days,
    "w" to 7.days, "sem." to 7.days, "Wo." to 7.days,
    "mo" to 30.days, "mois" to 30.days, "mes" to 30.days, "meses" to 30.days, "Mon." to 30.days,
    "y" to 365.days, "an" to 365.days, "ans" to 365.days, "año" to 365.days, "años" to 365.days, "J." to 365.days,
)
