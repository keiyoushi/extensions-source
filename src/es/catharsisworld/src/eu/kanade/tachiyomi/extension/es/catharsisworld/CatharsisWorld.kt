package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.util.LruCache
import eu.kanade.tachiyomi.multisrc.catharsis.Catharsis
import eu.kanade.tachiyomi.multisrc.catharsis.Step
import eu.kanade.tachiyomi.multisrc.catharsis.touch
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CatharsisWorld : Catharsis() {

    override val listingPath = "biblioteca"

    override val mangaPath = "manga"

    override val chapterSegment = "chapter/"

    override val sorts = listOf(
        "Recientes" to null,
        "Nuevos" to "date_created",
        "Populares" to "n_visitas",
        "Título" to "nombre",
    )

    override val popularSort = 2

    override val latestSort = 0

    override val statuses = STATUSES.keys.toList()

    override val ascendingOrder = "asc"

    // The ad-free reading pass the site offers after a rewarded ad
    override val prelude = "pass.js"

    // Genre ids by name, learned from the site's filter panel; a name it no longer offers maps to ""
    private val genreIds = LruCache<String, String>(100)

    override suspend fun readListing(url: HttpUrl): MangasPage {
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
            put("sort", if (pageUrl.queryParameter("order") == ascendingOrder) sort else "-$sort")
            pageUrl.queryParameter("genre")?.let { put("genre", it) }
        }
        val pick = if (names.isEmpty()) "null" else names.toJsonString()
        val listing = siteView.drive(pageUrl.toString(), "(${siteView.asset("listing.js")})(${query.toJsonString()}, $pick)") { result ->
            val page = result.parseAs<ListingPage>()
            when {
                page.tap != null -> touch(page.scale, page.width, page.height, page.tap)
                page.list != null -> Step.Done(page)
                else -> Step.Wait
            }
        }
        if (names.isNotEmpty()) {
            listing.ids.forEach { (name, id) -> genreIds.put(name, id.orEmpty()) }
            // The panel showed page 1; a later page loads by the ids it gave
            if ((url.queryParameter("page")?.toInt() ?: 1) > 1) return readListing(url)
        }
        val list = listing.list!!
        val page = query.getValue("page").toInt()
        return MangasPage(list.data.map { it.toSManga(it.id.toString(), baseUrl) }, page < list.totalPages)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = siteView.drive("$baseUrl/$listingPath", "(${siteView.asset("genres.js")})()") { result ->
        val panel = result.parseAs<GenrePanel>()
        when {
            panel.tap != null -> touch(panel.scale, panel.width, panel.height, panel.tap)
            panel.names != null -> Step.Done(panel.names.map { GenreOption(it, panel.ids[it]) }.toJsonElement())
            else -> Step.Wait
        }
    }

    override fun genres(data: JsonElement?) = data?.parseAs<List<GenreOption>>().orEmpty().map { it.name to (it.id ?: it.name) }

    override suspend fun readSeries(manga: SManga, known: List<SChapter>, withChapters: Boolean): SMangaUpdate {
        val series = siteView.drive(getMangaUrl(manga), "(${siteView.asset("series.js")})()") { result ->
            val page = result.parseAs<SeriesPage>()
            when {
                page.tap != null -> touch(page.scale, page.width, page.height, page.tap)
                page.title == null -> Step.Wait
                // A series opened by its slug is stored by its id, as the site's lists give it
                manga.url.toIntOrNull() == null && page.id == null -> Step.Wait
                withChapters && page.more != null -> touch(page.scale, page.width, page.height, page.more)
                // The chapter rows arrive after the details
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
            status = when (statusBadge?.let(STATUSES::get)) {
                "curso" -> SManga.ONGOING
                "pausado" -> SManga.ON_HIATUS
                "terminado" -> SManga.COMPLETED
                "abandonado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            genre = series.badges.filter { it != statusBadge }.joinToString().ifEmpty { null }
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

    /** How long ago "Hace 3 días" is. */
    private fun age(text: String): Duration? {
        if (text == "Hace un momento") return Duration.ZERO
        val match = AGE_REGEX.matchEntire(text) ?: return null
        val count = match.groupValues[1].toLongOrNull() ?: 1
        val unit = AGE_UNITS.entries.firstOrNull { match.groupValues[2].startsWith(it.key) }?.value ?: return null
        return unit * count.toDouble()
    }

    override val readerPages = "[data-page-index]"

    override val readerText = ".catha-novel-body"

    override fun readerRefusal(text: String): String? = when {
        "requiere iniciar sesión" in text -> "Inicia sesión en el sitio desde WebView para leer esta serie"
        "Este capítulo es nuevo" in text ->
            "Capítulo en acceso anticipado" + (UNLOCK_REGEX.find(text)?.let { " hasta el ${it.groupValues[1]}" } ?: "")
        "no es compatible" in text -> "El sitio rechazó el navegador de la app; inténtalo de nuevo"
        "VPN, proxy o servidor" in text -> "Desactiva la VPN o el proxy para leer en este sitio"
        else -> null
    }
}

private const val PAGE_SIZE = 24
private const val UNKNOWN_AUTHOR = "Desconocido"
private val UUID_REGEX = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""")
private val UNLOCK_REGEX = Regex("""a partir del (\d{4}-\d{2}-\d{2} \d{2}:\d{2} UTC)""")

// The status labels the site's list takes, and the values it sends for them
private val STATUSES = mapOf(
    "En Emisión" to "curso",
    "Hiatus" to "pausado",
    "Terminado" to "terminado",
    "Abandonado" to "abandonado",
)

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
