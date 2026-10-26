package eu.kanade.tachiyomi.multisrc.imperiomanhwa

import android.webkit.WebResourceResponse
import android.widget.Toast
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
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.time.Instant

abstract class ImperioManhwa :
    KeiSource(),
    ConfigurableSource {

    /** The site's search route, `paths.search` in its props */
    protected open val searchPath = "search"

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "es"),
        classLoader = this::class.java.classLoader!!,
    )

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseHttpUrl.host }

    override suspend fun getPopularManga(page: Int) = library(page, listOf("orden" to "vistas"))

    override suspend fun getLatestUpdates(page: Int) = library(page, emptyList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return library(page, filters.filterIsInstance<QueryFilter>().mapNotNull { filter -> filter.value?.let { filter.param to it } })
        }
        val url = baseHttpUrl.newBuilder()
            .addPathSegment(searchPath)
            .addPathSegment("")
            .addQueryParameter("q", query)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        return listing(url)
    }

    private suspend fun library(page: Int, params: List<Pair<String, String>>): MangasPage {
        val url = baseHttpUrl.newBuilder().apply {
            addPathSegment("manga")
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
            addPathSegment("")
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        return listing(url)
    }

    private suspend fun listing(url: HttpUrl): MangasPage {
        val props = client.get(url).asJsoup().inertiaPage<ListingProps>().props
        return MangasPage(props.series.map { it.toSManga() }, props.pagination.next != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHttpUrl.host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() && it != "page" } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val props = client.get(getMangaUrl(manga)).asJsoup().inertiaPage<SeriesProps>().props
        // A labelled tag row holds something else, like the series' groups
        props.series.applyTo(manga, intl["alt_names"], withTags = props.tagsLabel == null)
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        // Dates from the series page, the rest from the full chapter list
        val all = if (props.chaptersPagination.last > 1) {
            client.get(baseHttpUrl.resolve(props.series.url)!!.resolve("${props.paths.chapters}.json")!!).parseAs<ChapterListDto>().items
        } else {
            props.chapters
        }
        val dated = props.chapters.associateBy { it.slug }
        val withDates = all.map { dated[it.slug] ?: it }.let { it.zip(uploadDates(it)) }
        return SMangaUpdate(
            manga,
            withDates.filterNot { (chapter, _) -> hideLocked && chapter.locked }
                .map { (chapter, date) -> chapter.toSChapter(props.series.url, date) },
        )
    }

    // The full list's labels have no year ("24 Sep", "24 ago."). Newest first from the series page's dated
    // rows, each takes the latest year that isn't more than two months after the row above (rows run up to
    // a month out of order)
    private fun uploadDates(chapters: List<ChapterDto>): List<Long> {
        var previous: LocalDate? = null
        return chapters.map { chapter ->
            val exact = Instant.tryParse(chapter.publishedAt)
            if (exact != 0L) {
                previous = LocalDate.ofEpochDay(exact / DAY_MILLIS)
                return@map exact
            }
            val above = previous ?: return@map 0L
            val match = chapter.publishedLabel?.let(DATE_LABEL::matchEntire) ?: return@map 0L
            val month = MONTHS[match.groupValues[2].lowercase()] ?: return@map 0L
            val monthDay = MonthDay.of(month, match.groupValues[1].toInt())
            val date = (above.year + 1 downTo above.year - 1).map(monthDay::atYear)
                .first { !it.isAfter(above.plusDays(62)) }
            previous = date
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }

    // Madara-era search results: a post id, with the path in memo
    override fun getMangaUrl(manga: SManga): String {
        val path = manga.memo["path"]?.stringOrNull?.takeIf { manga.url.all(Char::isDigit) } ?: manga.url
        return baseUrl + path
    }

    // Madara-era chapters: a bare slug, with the series path in memo
    override fun getChapterUrl(chapter: SChapter): String {
        val mangaPath = chapter.memo["mangaPath"]?.stringOrNull?.takeIf { '/' !in chapter.url }
        return baseUrl + (mangaPath?.let { "${it.trimEnd('/')}/${chapter.url}/" } ?: chapter.url)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val page = document.inertiaPage<JsonElement>()
        if (page.component == "Reader/Locked") return emptyList()

        val props = page.props.parseAs<ReaderProps>()
        // Inline first page, then the sealed rest from the site's reader
        val pages = if (props.dims.isEmpty()) {
            props.pages
        } else {
            val entry = document.selectFirst("script[type=module][src*=/build/assets/]")!!.absUrl("src")
            val sealed = openReader(ReaderInput(entry, props.reader, props.chapter.id, props.series.slug)).associateBy { it.n }
            val inline = props.pages.size
            props.pages + (inline + 1..inline + props.dims.size).map { sealed[it] ?: throw IOException("Page $it missing") }
        }
        val host = baseHttpUrl.host
        return pages.mapIndexed { index, page ->
            val imageUrl = page.imageUrl.toHttpUrl()
            if (imageUrl.host != host && !imageUrl.host.endsWith(".$host")) throw IOException("Unexpected image host")
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    // One reader WebView at a time
    private val readerMutex = Mutex()

    private suspend fun openReader(input: ReaderInput): List<PageDto> = readerMutex.withLock {
        val bridge = "r" + Random.nextLong().toULong().toString(36)
        val script = "(${asset("reader.js")})(${input.toJsonString()})" +
            ".then((pages) => $bridge.postMessage(JSON.stringify({ pages })), (e) => $bridge.postMessage(JSON.stringify({ error: String(e) })))"
        val result = try {
            runWebView<ReaderResult> {
                blockImages = true
                interceptRequest { request ->
                    val host = request.url.host
                    if (host == null || host == baseHttpUrl.host) null else WebResourceResponse("text/plain", null, 403, "Blocked", null, ByteArrayInputStream(ByteArray(0)))
                }
                webMessageBridge(bridge, setOf(baseUrl)) { resolve(it.parseAs()) }
                onPageFinished { evaluateJs(script) }
                loadData(baseUrl, "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        result.error?.let { throw IOException(it) }
        result.pages!!
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val props = client.get(baseUrl).asJsoup().inertiaPage<HomeProps>().props
        return FilterData(props.genres, props.content.sections).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        val all = intl["all"] to ""
        return FilterList(
            listOfNotNull(
                QueryFilter(intl["sort_title"], "orden", SORTS.map { intl["sort_$it"] to it }, SORTS.indexOf("vistas")),
                filterData?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    QueryFilter(intl["genre_title"], "genero", listOf(all) + genres.map { it.name to it.slug })
                },
                QueryFilter(intl["type_title"], "tipo", listOf(all) + TYPES.map { it.replaceFirstChar(Char::uppercase) to it }),
                QueryFilter(intl["status_title"], "estado", listOf(all) + STATUSES.map { intl["status_$it"] to it }),
                filterData?.sections?.takeIf { it.isNotEmpty() }?.let { sections ->
                    QueryFilter(intl["section_title"], "seccion", listOf(all) + sections.map { it.label to it.code })
                },
            ),
        )
    }

    private inline fun <reified T> Document.inertiaPage(): InertiaPage<T> = selectFirst("script[data-page=app]")!!.data().parseAs()

    private val preferences by getPreferencesLazy()

    private val hideLocked get() = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = intl["hide_locked_title"]
            summary = intl["hide_locked_summary"]
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, intl["hide_locked_toast"], Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true

        private val SORTS = listOf("reciente", "nuevas", "vistas", "valoradas", "seguidas", "alfabetico")
        private val TYPES = listOf("manga", "manhwa", "manhua", "webtoon")
        private val STATUSES = listOf("ongoing", "completed", "hiatus", "cancelled")

        private const val DAY_MILLIS = 86_400_000L

        private val DATE_LABEL = Regex("""(\d{1,2}) (\p{L}{3})\p{L}*\.?""")

        // English and Spanish month abbreviations
        private val MONTHS = listOf(
            "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
            "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
        ).withIndex().associate { (index, name) -> name to index % 12 + 1 }
    }
}
