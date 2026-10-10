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
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class TempleScan :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy {
        edit().remove("pref_rsc_keys").apply()
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1)
        addInterceptor(ChallengeInterceptor())
    }

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
                series.alternativeNames?.contains(query, ignoreCase = true) == true

            val statusFilter = status == null || series.status == status

            queryFilter && statusFilter
        }.let {
            val order = filters.firstInstanceOrNull<OrderFilter>()?.selected

            when (order) {
                "updated" -> it.sortedByDescending { series -> series.updated }
                "created" -> it.sortedByDescending { series -> series.created }
                "views" -> it.sortedByDescending { series -> series.views }
                else -> it
            }
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
        val mangaUrl = "/comic/$slug"
        val manga = SManga.create().apply {
            this.url = mangaUrl
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = mangaUrl
            }
    }

    // =========================== Manga Updates ============================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast('/')
        val document = client.get("$baseUrl/comic/$slug").asJsoup()

        val series = document.jsonLd<ComicSeriesLd> { it.isSeries }
        val seriesData = document.mappedPayload<SeriesData>(SERIES_FIELDS)
        // The status only lives in the browse catalog; the detail page renders it without a stable hook.
        val catalogEntry = fetchCatalog().firstOrNull { it.slug == slug }

        val genres = series?.genre.orEmpty()
        val adult = genres.any { it.equals("+18", ignoreCase = true) }

        val manga = SManga.create().apply {
            url = "/comic/$slug"
            title = series?.name ?: catalogEntry?.title ?: slug
            thumbnail_url = series?.image ?: catalogEntry?.thumbnail
            author = series?.author?.name
            status = when (catalogEntry?.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "hiatus" -> SManga.ON_HIATUS
                "completed" -> SManga.COMPLETED
                "canceled", "dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            genre = buildList {
                catalogEntry?.badge?.let { add(it) }
                if (adult) add("Adult")
                addAll(genres.filterNot { it.equals("+18", ignoreCase = true) })
            }.joinToString()
            description = buildString {
                append(document.synopsis() ?: series?.description.orEmpty())
                series?.alternateName?.takeIf { it.isNotBlank() }?.let {
                    append("\n\nAlternative Name: ").append(it)
                }
            }
        }

        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED_CHAPTERS, true)
        val chapterList = seriesData?.chapters.orEmpty()
            .filter { !hideLocked || it.price <= 0 }
            .map { chapter ->
                SChapter.create().apply {
                    url = "/comic/$slug/${chapter.slug}"
                    name = buildString {
                        if (chapter.price > 0) append("\uD83D\uDD12 ")
                        append(chapter.name)
                        if (!chapter.title.isNullOrBlank()) {
                            append(": ", chapter.title)
                        }
                    }
                    date_upload = chapter.created
                }
            }

        return SMangaUpdate(manga, chapterList)
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(baseUrl + chapter.url).asJsoup().mappedPayload<PagesList>(PAGE_FIELDS)
            ?: return emptyList()
        return data.images.mapIndexed { idx, url ->
            Page(idx, imageUrl = url)
        }
    }

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED_CHAPTERS
            title = "Hide locked chapters"
            summary = "Hide early access chapters that require a subscription. If disabled, they are shown with a \uD83D\uDD12 prefix but stay unreadable without a subscription."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    // ============================= Utilities ==============================

    private suspend fun fetchCatalog(): List<BrowseSeries> = client.get("$baseUrl/comics")
        .asJsoup()
        .mappedPayload<List<BrowseSeries>>(CATALOG_FIELDS, isList = true)
        .orEmpty()

    /**
     * Reads the RSC payload node holding [fields] and decodes it with the site's current field keys.
     *
     * The keys come from the table cached by [refreshRscKeys]. If they do not match this payload,
     * or match but decode wrongly because the site swapped keys between fields, the table is re-read
     * from the client bundle once.
     */
    private suspend inline fun <reified T> Document.mappedPayload(fields: List<String>, isList: Boolean = false): T? {
        cachedRscKeys()?.let { keys ->
            try {
                decodePayload<T>(fields, keys, isList)?.let { return it }
            } catch (_: SerializationException) {
            }
        }

        return decodePayload<T>(fields, refreshRscKeys(this), isList)
    }

    private inline fun <reified T> Document.decodePayload(fields: List<String>, keys: Map<String, String>, isList: Boolean): T? = extractNextJs<JsonElement>(RscKeys.payloadPredicate(fields, keys, isList))
        ?.let { RscKeys.remap(it, keys).parseAs<T>() }

    private fun cachedRscKeys(): Map<String, String>? = preferences.getString(PREF_RSC_KEYS, null)
        ?.parseAs<Map<String, String>>()
        ?.takeIf { it.isNotEmpty() }

    /** Decodes this page's payload keys with the site's client bundle and caches them for later runs. */
    private suspend fun refreshRscKeys(document: Document): Map<String, String> {
        val cached = cachedRscKeys().orEmpty()
        // Known keys let the decoder be found on pages that rename nothing, such as locked chapters.
        val payloadKeys = buildSet {
            addAll(cached.values)
            document.extractNextJs<JsonElement> { element ->
                if (element is JsonObject) addAll(element.keys)
                false
            }
        }
        val host = baseUrl.toHttpUrl().host
        val chunks = document.select("script[src]")
            .mapNotNull { element -> element.absUrl("src").toHttpUrlOrNull() }
            .filter { it.host == host && it.encodedPath.startsWith(CHUNK_PATH) }
            .map { it.toString() }
            .distinct()

        val decoded = RscKeys.decodeTable(baseUrl, chunks, payloadKeys, RSC_FIELDS)
            ?: error("Could not determine the site's RSC field-key table")
        // A key the site has given to another field must not keep its old name.
        val keys = cached.filterValues { it !in decoded.values } + decoded

        preferences.edit().putString(PREF_RSC_KEYS, keys.toJsonString()).apply()
        return keys
    }

    private inline fun <reified T> Document.jsonLd(predicate: (T) -> Boolean): T? = select("script[type=application/ld+json]")
        .mapNotNull { runCatching { it.data().parseAs<T>() }.getOrNull() }
        .firstOrNull(predicate)

    private fun Document.synopsis(): String? = selectFirst("#series-synopsis-text")?.let { element ->
        element.select("p").takeIf { it.isNotEmpty() }?.joinToString("\n\n") { it.text() }
            ?: element.text()
    }?.takeIf { it.isNotBlank() }

    companion object {
        private const val CHUNK_PATH = "/_next/static/chunks/"

        /** Identifies the browse catalog: the two fields every series entry always carries. */
        private val CATALOG_FIELDS = listOf("title", "series_slug")

        /** Identifies a series' chapter payload. Both are renamed by the site, unlike `seriesData`. */
        private val SERIES_FIELDS = listOf("series_slug", "Season")

        private val PAGE_FIELDS = listOf("images")

        /** Every field looked up through the table; identifies the site's decoder. */
        private val RSC_FIELDS = (CATALOG_FIELDS + SERIES_FIELDS + PAGE_FIELDS).distinct()

        private const val PREF_HIDE_LOCKED_CHAPTERS = "pref_hide_locked_chapters"
        private const val PREF_RSC_KEYS = "pref_rsc_key_table"
    }
}
