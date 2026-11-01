package eu.kanade.tachiyomi.extension.en.templescan

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException

@Source
abstract class TempleScan :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy {
        edit().remove("pref_rsc_keys").remove("pref_rsc_key_table").apply()
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1)
    }

    private val challengeMutex = Mutex()

    private var challengePassedAt = 0L

    private var challengeFailedAt = 0L

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", OrderFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", OrderFilter.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val catalog = fetchCatalog()
        return parseDirectory(catalog, query, filters)
    }

    private fun parseDirectory(series: List<BrowseSeries>, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected
        val mangaList = series.filter { series ->

            val queryFilter = query.isBlank() ||
                series.title.contains(query, ignoreCase = true) ||
                series.fields.alternativeNames?.contains(query, ignoreCase = true) == true

            val statusFilter = status == null || series.fields.status == status

            queryFilter && statusFilter
        }.let {
            val order = filters.firstInstanceOrNull<OrderFilter>()

            val sorted = when (order?.selected) {
                "updated" -> it.sortedByDescending { series -> series.updated }
                "created" -> it.sortedByDescending { series -> series.created }
                "views" -> it.sortedByDescending { series -> series.fields.views }
                "title" -> it.sortedByDescending { series -> series.title.trim().lowercase() }
                else -> it
            }
            if (order?.ascending == true) sorted.reversed() else sorted
        }

        return MangasPage(
            mangas = mangaList.map { it.toSManga() },
            hasNextPage = false,
        )
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "comic") {
            return null
        }

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            this.url = "/comic/$slug"
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast('/')
        val document = fetchDocument(getMangaUrl(manga))
        val details = if (fetchDetails) parseDetails(document, slug) else manga
        return SMangaUpdate(details, parseChapters(document, slug))
    }

    private suspend fun parseDetails(document: Document, slug: String): SManga {
        val series = document.jsonLd<ComicSeriesLd> { it.isSeries }
        // The status and type only live in the browse catalog.
        val catalogEntry = fetchCatalog().firstOrNull { it.slug == slug }

        val genres = series?.genre.orEmpty()
        val badge = catalogEntry?.fields?.badge
        val adult = genres.any { it.equals("+18", ignoreCase = true) } || badge?.contains("+18") == true

        return SManga.create().apply {
            url = "/comic/$slug"
            title = series?.name ?: catalogEntry!!.title
            thumbnail_url = series?.image ?: catalogEntry?.fields?.thumbnail
            author = document.info("Author") ?: series?.author?.name
            status = parseStatus(catalogEntry?.fields?.status)
            genre = buildList {
                badge?.withoutRating()?.let { add(it) }
                if (adult) add("Adult")
                addAll(genres.filterNot { it.equals("+18", ignoreCase = true) })
            }.joinToString().ifEmpty { null }
            description = buildString {
                append(document.synopsis() ?: series?.description.orEmpty())
                val info = listOfNotNull(
                    document.info("Studio")?.let { "Studio $it" },
                    document.info("Released")?.let { "Released $it" },
                )
                if (info.isNotEmpty()) append("\n\n").append(info.joinToString(" · "))
                // Commas also appear inside names; only `|` always separates them
                val altNames = (series?.alternateName ?: document.info("Also known as"))
                    ?.split('|')?.map(String::trim)?.filter(String::isNotEmpty)?.distinct().orEmpty()
                if (altNames.isNotEmpty()) {
                    append("\n\n**Alternative names:**")
                    altNames.forEach { append("\n- ").append(it) }
                }
            }.trim().ifEmpty { null }
        }
    }

    private fun parseChapters(document: Document, slug: String): List<SChapter> {
        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED_CHAPTERS, true)
        val seriesPage = document.extractNextJs<SeriesPage> { it is JsonObject && "seriesData" in it }!!
        return seriesPage.chapters
            .filter { !hideLocked || !it.premium }
            .map { chapter ->
                SChapter.create().apply {
                    url = "/comic/$slug/${chapter.slug}"
                    name = buildString {
                        if (chapter.premium) append(LOCK)
                        append(chapter.name)
                        if (!chapter.title.isNullOrBlank()) {
                            append(": ", chapter.title)
                        }
                    }
                    date_upload = chapter.createdAt
                }
            }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = fetchDocument(getChapterUrl(chapter)).extractNextJs<List<String>> { element ->
            element is JsonArray && (element.firstOrNull() as? JsonPrimitive)?.stringOrNull?.contains("/uploads/") == true
        }
        return images.orEmpty().mapIndexed { idx, url ->
            Page(idx, imageUrl = url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED_CHAPTERS
            title = "Hide locked chapters"
            summary = "Hide early access chapters that require a subscription. If disabled, they are shown with a \uD83D\uDD12 prefix but stay unreadable without a subscription."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    // The site's WAF blocks the default `Cache-Control: max-age=600`
    private suspend fun getPage(url: String) = client.get(url, Fetch.DOCUMENT, cacheControl = CacheControl.Builder().build()).asJsoup()

    private suspend fun fetchDocument(url: String): Document {
        val startedAt = System.currentTimeMillis()
        val document = getPage(url)
        if (!document.isChallenge) return document

        passChallenge(document, startedAt)
        return getPage(url).also { if (it.isChallenge) throw IOException(SOLVE_MANUALLY) }
    }

    private val Document.isChallenge get() = location().toHttpUrl().encodedPath == "/challenge"

    /** Solves the site's Turnstile and posts the token for its `tt-challenge` cookie. */
    private suspend fun passChallenge(challengePage: Document, startedAt: Long) = challengeMutex.withLock {
        // Another request may have passed it while this one waited
        if (challengePassedAt > startedAt) return@withLock
        // A challenge that just failed to solve would fail again; leave it to the user.
        if (System.currentTimeMillis() - challengeFailedAt < CHALLENGE_BACKOFF) throw IOException(SOLVE_MANUALLY)

        try {
            val chunk = challengePage.selectFirst("script[src*=/app/challenge/page-]")!!.absUrl("src")
            val siteKey = TURNSTILE_KEY.find(client.get(chunk, Fetch.SCRIPT).body.string())!!.value
            val token = getTurnstileToken("$baseUrl/challenge", siteKey)
            client.post("$baseUrl/api/turnstile/verify", Fetch.XHR.from("/challenge"), TurnstileToken(token).toJsonRequestBody()).close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            challengeFailedAt = System.currentTimeMillis()
            throw IOException(SOLVE_MANUALLY, e)
        }
        challengePassedAt = System.currentTimeMillis()
    }

    private suspend fun fetchCatalog(): List<BrowseSeries> = fetchDocument("$baseUrl/comics")
        .extractNextJs<List<JsonObject>> { element ->
            element is JsonArray && (element.firstOrNull() as? JsonObject)?.containsKey("total_views") == true
        }!!
        .map(::BrowseSeries)

    private fun Document.info(label: String) = select("dl dt").firstOrNull { it.text() == label }
        ?.nextElementSibling()?.textOrNull()

    private inline fun <reified T> Document.jsonLd(predicate: (T) -> Boolean): T? = select("script[type=application/ld+json]")
        .mapNotNull { runCatching { it.data().parseAs<T>() }.getOrNull() }
        .firstOrNull(predicate)

    private fun Document.synopsis(): String? = selectFirst("#series-synopsis-text")?.let { element ->
        element.select("p").takeIf { it.isNotEmpty() }?.joinToString("\n\n") { it.text() }
            ?: element.text()
    }?.takeIf { it.isNotBlank() }

    companion object {
        private const val PREF_HIDE_LOCKED_CHAPTERS = "pref_hide_locked_chapters"
        private const val LOCK = "\uD83D\uDD12 "
        private const val SOLVE_MANUALLY = "Open in WebView to pass the site's verification"
        private const val CHALLENGE_BACKOFF = 5 * 60 * 1000L
        private val TURNSTILE_KEY = Regex("0x4[A-Za-z0-9_-]{20,}")
    }
}
