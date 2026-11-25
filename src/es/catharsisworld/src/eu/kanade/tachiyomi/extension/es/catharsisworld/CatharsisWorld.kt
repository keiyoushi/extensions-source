package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.util.LruCache
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.cronet.cronet
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CatharsisWorld :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::webViewInterceptor)
        .cronet()
        .callTimeout(Duration.ZERO)

    // Lazy for the source's id; baseUrl is read on each load
    private val siteView by lazy { SiteView({ baseUrl }, PRELUDE) { headers["User-Agent"]!! } }

    override suspend fun getPopularManga(page: Int) = readListing(listingUrl(page, "", POPULAR_SORT, null, null, emptyList()))

    override suspend fun getLatestUpdates(page: Int) = readListing(listingUrl(page, "", LATEST_SORT, null, null, emptyList()))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        return readListing(
            listingUrl(
                page = page,
                query = query,
                sort = sort?.state?.index ?: POPULAR_SORT,
                ascending = sort?.state?.ascending,
                status = filters.firstInstanceOrNull<StatusFilter>()?.value,
                genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.id },
                letter = filters.firstInstanceOrNull<LetterFilter>()?.value,
            ),
        )
    }

    private fun listingUrl(page: Int, query: String, sort: Int, ascending: Boolean?, status: String?, genres: List<String>, letter: String? = null) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment(LISTING_PATH)
        if (page > 1) addQueryParameter("page", page.toString())
        if (query.isNotBlank()) addQueryParameter("search", query.trim())
        letter?.let { addQueryParameter("letter", it) }
        status?.let { addQueryParameter("status", it) }
        SORTS[sort].second?.let { addQueryParameter("sort", it) }
        if (ascending == true) addQueryParameter("order", ASCENDING_ORDER)
        if (genres.isNotEmpty()) addQueryParameter("genre", genres.joinToString("|"))
    }.build()

    // Genre ids by name, learned from the site's filter panel; a name it no longer offers maps to ""
    private val genreIds = LruCache<String, String>(100)

    private suspend fun readListing(url: HttpUrl): MangasPage {
        // A genre is its id, or its name when the filter data had no id for it
        val genres = url.queryParameter("genre")?.split('|').orEmpty().map { genreIds[it] ?: it }
        val names = genres.filterNot { UUID_REGEX.matches(it) || it.isEmpty() }
        val ids = genres.filter { UUID_REGEX.matches(it) }
        val pageUrl = url.newBuilder().apply {
            removeAllQueryParameters("genre")
            if (ids.isNotEmpty()) setQueryParameter("genre", ids.joinToString("|"))
            // The panel's picks show on the first page
            if (names.isNotEmpty()) removeAllQueryParameters("page")
        }.build()

        // The request the page sends for its url, which names its cached response
        val sort = pageUrl.queryParameter("sort") ?: "fecha_ultimo_capitulo"
        val query = buildMap {
            put("limit", PAGE_SIZE.toString())
            put("page", pageUrl.queryParameter("page") ?: "1")
            pageUrl.queryParameter("search")?.let { put("name", it) }
            pageUrl.queryParameter("letter")?.let { put("letter", it) }
            pageUrl.queryParameter("status")?.let { put("status", STATUSES.getValue(it)) }
            put("sort", if (pageUrl.queryParameter("order") == ASCENDING_ORDER) sort else "-$sort")
            pageUrl.queryParameter("genre")?.let { put("genre", it) }
        }
        val pick = if (names.isEmpty()) "null" else names.toJsonString()
        val listing = siteView.drive(pageUrl.toString(), "(${siteView.asset("listing.js")})(${query.toJsonString()}, $pick)") { result ->
            val page = result.parseAs<ListingPage>()
            when {
                page.error != null -> throw IOException("Error HTTP ${page.error}")
                page.tap != null -> touch(page.scale, page.width, page.height, page.tap)
                page.cards != null -> Step.Done(page)
                else -> Step.Wait
            }
        }
        if (names.isNotEmpty()) {
            listing.ids.forEach { (name, id) -> genreIds.put(name, id.orEmpty()) }
            // The panel showed page 1; a later page loads by the ids it gave
            if ((url.queryParameter("page")?.toInt() ?: 1) > 1) return readListing(url)
        }
        return MangasPage(listing.cards!!.map { it.toSManga(baseUrl) }, listing.next)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$MANGA_PATH/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != MANGA_PATH) return null
        val key = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = key }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = readSeries(manga, chapters, fetchChapters)

    private suspend fun readSeries(manga: SManga, known: List<SChapter>, withChapters: Boolean): SMangaUpdate {
        val series = siteView.drive(getMangaUrl(manga), "(${siteView.asset("series.js")})()") { result ->
            val page = result.parseAs<SeriesPage>()
            when {
                page.tap != null -> touch(page.scale, page.width, page.height, page.tap)
                page.title == null -> Step.Wait
                // A series opened by its slug is stored by its id, as the site's lists give it
                manga.url.toIntOrNull() == null && page.id == null -> Step.Wait
                withChapters && page.more != null -> touch(page.scale, page.width, page.height, page.more)
                withChapters && page.rows.size < page.count -> Step.Wait
                else -> Step.Done(page)
            }
        }

        val details = SManga.create().apply {
            url = series.id?.takeIf { manga.url.toIntOrNull() == null } ?: manga.url
            title = series.title!!
            thumbnail_url = series.cover?.let { baseUrl.toHttpUrl().resolve(it)?.newBuilder()?.query(null)?.build()?.toString() }
            author = series.author?.takeUnless { it == UNKNOWN_AUTHOR }
            val statusBadge = series.badges.firstOrNull { it in STATUSES }
            status = statusOf(series.badges)
            genre = series.badges.filter { it != statusBadge }.joinToString { badgeName(it) }.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .rating(series.rating?.takeIf { (series.votes ?: 0) > 0 }, max = 5, votes = series.votes)
                .description(series.description)
                .altTitles(listOfNotNull(series.altTitle?.takeIf { it != series.title }), "Nombres alternativos")
                .build()
        }
        if (!withChapters) return SMangaUpdate(details, known)

        // The site shows only how long ago a chapter came out, so a chapter keeps the date it was first stored with
        val stored = known.associate { it.url to it.date_upload }
        val now = Clock.System.now()
        val scanlator = series.scans.joinToString().ifEmpty { null }
        val chapters = series.rows
            .map { row ->
                val number = baseUrl.toHttpUrl().resolve(row.href)!!.pathSegments.last()
                SChapter.create().apply {
                    url = "${details.url}/$number"
                    name = row.title ?: row.heading
                    number.toFloatOrNull()?.let { chapter_number = it }
                    date_upload = stored[url]?.takeIf { it > 0 } ?: row.age?.let(::age)?.let { (now - it).toEpochMilliseconds() } ?: 0L
                    this.scanlator = scanlator
                }
            }
            .distinctBy { it.url }
            .reversed()
        return SMangaUpdate(details, chapters)
    }

    // The site spells some types "Manwha" or in lower case
    private fun badgeName(badge: String) = when (badge.lowercase()) {
        "manwha" -> "Manhwa"
        else -> badge.replaceFirstChar(Char::uppercase)
    }

    /** How long ago "Hace 3 días" is. */
    private fun age(text: String): Duration? {
        if (text == "Hace un momento") return Duration.ZERO
        val match = AGE_REGEX.matchEntire(text) ?: return null
        val count = match.groupValues[1].toLongOrNull() ?: 1
        val unit = AGE_UNITS.entries.firstOrNull { match.groupValues[2].startsWith(it.key) }?.value ?: return null
        return unit * count.toDouble()
    }

    // Stored as <series>/<chapter number>
    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/$MANGA_PATH/${chapter.url.substringBeforeLast('/')}/$CHAPTER_SEGMENT${chapter.url.substringAfterLast('/')}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val hd = preferences.getBoolean(HD_PREF, false)
        val count = reader(chapterUrl.toHttpUrl().encodedPath, 0, hd).pageCount.await()
        val fragment = if (hd) HD_PAGE_FRAGMENT else PAGE_FRAGMENT
        return List(count) { index -> Page(index, imageUrl = "$chapterUrl#$fragment$index") }
    }

    private fun readerRefusal(text: String): String? = when {
        "requiere iniciar sesión" in text -> "Inicia sesión en el sitio desde WebView para leer esta serie"
        "Este capítulo es nuevo" in text ->
            "Capítulo en acceso anticipado" + (UNLOCK_REGEX.find(text)?.let { " hasta el ${it.groupValues[1]}" } ?: "")
        "no es compatible" in text -> "El sitio rechazó el navegador de la app; inténtalo de nuevo"
        "VPN, proxy o servidor" in text -> "Desactiva la VPN o el proxy para leer en este sitio"
        else -> null
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = siteView.drive("$baseUrl/$LISTING_PATH", "(${siteView.asset("genres.js")})()") { result ->
        val panel = result.parseAs<GenrePanel>()
        when {
            panel.tap != null -> touch(panel.scale, panel.width, panel.height, panel.tap)
            panel.names != null -> Step.Done(panel.names.map { GenreOption(it, null) }.toJsonElement())
            else -> Step.Wait
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter(SORTS.map { it.first }, POPULAR_SORT))
            add(StatusFilter(STATUSES.keys.toList()))
            add(LetterFilter())
            data?.parseAs<List<GenreOption>>()?.takeIf { it.isNotEmpty() }?.let { genres ->
                add(GenreFilter(genres.map { GenreCheckBox(it.name, it.id ?: it.name) }))
            }
        },
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HD_PREF
            title = "Calidad HD"
            summary = "Páginas de 800 px en lugar de 600; carga más lenta"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val readers = linkedMapOf<String, ChapterReader>()

    /** The chapter's open reader in this quality that can still deliver page [index], or a new one starting there. */
    @Synchronized
    private fun reader(chapterPath: String, index: Int, hd: Boolean): ChapterReader {
        readers[chapterPath]?.takeIf { it.hd == hd && it.covers(index) }?.let { return it }
        readers.remove(chapterPath)?.close()
        while (readers.size >= MAX_READERS) readers.remove(readers.keys.first())?.close()
        val dir = File(applicationContext.cacheDir, "catharsis-pages/$id/${chapterPath.hashCode().toUInt()}-$index${if (hd) "-hd" else ""}")
        val chapterUrl = "$baseUrl$chapterPath"
        return ChapterReader(chapterUrl, index, hd, siteView, dir, readerScope, READER_PAGES, READER_TEXT, ::readerRefusal) { readerPass(chapterUrl) }
            .also { readers[chapterPath] = it }
    }

    private val apiHeaders get() = headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("System", "catharsis")
        .set("X-FK-Sistema", "3")
        .build()

    private val readerPassMutex = Mutex()

    private suspend fun readerPass(chapterUrl: String) = readerPassMutex.withLock {
        val fetch = Fetch.XHR.from(chapterUrl)
        val system = client.get("$baseUrl/api/system", apiHeaders, fetch).parseAs<SystemDto>()
        val capUrl = system.capApiEndpoint.toHttpUrl()
        val challenge = client.post(capUrl.resolve("challenge")!!, fetch, RequestBody.EMPTY).parseAs<CapChallengeResponse>()
        val solutions = (1..challenge.challenge.count).map { i ->
            solveCap(
                salt = capPrng("${challenge.token}$i", challenge.challenge.saltSize),
                target = capPrng("${challenge.token}${i}d", challenge.challenge.difficulty),
            )
        }
        val redeemed = client.post(capUrl.resolve("redeem")!!, fetch, CapRedeemRequest(challenge.token, solutions).toJsonRequestBody())
            .parseAs<CapRedeemResponse>()
        val capToken = redeemed.token?.takeIf { redeemed.success } ?: throw IOException("Reader verification failed")
        client.post("$baseUrl/lector/pase", fetch, ReaderPassRequest(capToken, system.capSitekey).toJsonRequestBody()).close()
    }

    // FNV-1a seeded xorshift32, as hex
    private fun capPrng(seed: String, length: Int): String {
        var state = seed.fold(FNV_OFFSET_BASIS) { hash, char -> (hash xor char.code) * FNV_PRIME }
        return buildString {
            while (this.length < length) {
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                append(state.toUInt().toString(16).padStart(8, '0'))
            }
        }.take(length)
    }

    private fun solveCap(salt: String, target: String): Int = generateSequence(0) { it + 1 }
        .first { "$salt$it".sha256().toHexString().startsWith(target) }

    private val covers by lazy { Covers({ baseUrl }, File(applicationContext.cacheDir, "catharsis-covers/$id"), readerScope) { headers["User-Agent"]!! } }

    private fun webViewInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrl.toHttpUrl().host) return chain.proceed(request)
        val fragment = request.url.fragment.orEmpty()
        val hd = fragment.startsWith(HD_PAGE_FRAGMENT)
        val index = fragment.takeIf { hd || it.startsWith(PAGE_FRAGMENT) }?.substringAfterLast('-')?.toIntOrNull()
        if (index != null) {
            val file = awaitFile { reader(request.url.encodedPath, index, hd).page(index).await() }
            return fileResponse(request, file, "image/jpeg")
        }
        if (request.url.pathSegments.first() != "assets") return chain.proceed(request)
        val cover = awaitFile { covers.get(request.url.newBuilder().query(null).fragment(null).build().toString()).await() }
        return fileResponse(request, cover.file, cover.type)
    }

    private fun <T> awaitFile(block: suspend () -> T): T = try {
        runBlocking { withTimeout(FILE_TIMEOUT) { block() } }
    } catch (e: Throwable) {
        throw e as? IOException ?: IOException(e.message ?: "La página del sitio no cargó", e)
    }

    private fun fileResponse(request: Request, file: File, type: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(file.source().buffer().asResponseBody(type.toMediaType(), file.length()))
        .build()
}

private const val LISTING_PATH = "biblioteca"
private const val MANGA_PATH = "manga"

// What comes between the series and the chapter number in a chapter's path
private const val CHAPTER_SEGMENT = "chapter/"

private const val PRELUDE = "pass.js"

// The reader's page elements, each holding the canvas its page is drawn on, in reading order, and its text reader for
// novels
private const val READER_PAGES = ":has(> canvas)"
private const val READER_TEXT = ".catha-novel-body"

// The series list's sorts as its page url takes them
private val SORTS = listOf(
    "Recientes" to null,
    "Nuevos" to "date_created",
    "Populares" to "n_visitas",
    "Título" to "nombre",
)
private const val POPULAR_SORT = 2
private const val LATEST_SORT = 0
private const val ASCENDING_ORDER = "asc"

private const val PAGE_SIZE = 24
private const val UNKNOWN_AUTHOR = "Desconocido"
private const val PAGE_FRAGMENT = "catharsis-page-"
private const val HD_PAGE_FRAGMENT = "catharsis-hd-page-"
private const val HD_PREF = "hd_quality"
private const val MAX_READERS = 2
private const val FNV_OFFSET_BASIS = 0x811c9dc5.toInt()
private const val FNV_PRIME = 0x01000193
private val FILE_TIMEOUT = 5.minutes
private val UUID_REGEX = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""")
private val UNLOCK_REGEX = Regex("""a partir del (\d{4}-\d{2}-\d{2} \d{2}:\d{2} UTC)""")

// The status labels the site's list takes, and the values it sends for them
private val STATUSES = mapOf(
    "En Emisión" to "curso",
    "Hiatus" to "pausado",
    "Terminado" to "terminado",
    "Abandonado" to "abandonado",
)

/** The status of the first of [labels] that is a status label. */
internal fun statusOf(labels: List<String>) = when (labels.firstNotNullOfOrNull(STATUSES::get)) {
    "curso" -> SManga.ONGOING
    "pausado" -> SManga.ON_HIATUS
    "terminado" -> SManga.COMPLETED
    "abandonado" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private val AGE_REGEX = Regex("""Hace (\d+|una?) (\S+)""")
private val AGE_UNITS = mapOf(
    "segundo" to 1.seconds,
    "minuto" to 1.minutes,
    "hora" to 1.hours,
    "día" to 1.days,
    "semana" to 7.days,
    "mes" to 30.days,
    "año" to 365.days,
)
