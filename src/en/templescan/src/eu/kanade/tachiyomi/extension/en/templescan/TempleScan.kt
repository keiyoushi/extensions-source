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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

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

    // Headers of an image load in Chrome for Android
    override fun Headers.Builder.configureHeaders() = apply {
        removeAll("Origin")
        set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        set("Sec-Fetch-Site", "same-site")
        set("Sec-Fetch-Mode", "no-cors")
        set("Sec-Fetch-Dest", "image")
        set("Priority", "i")
    }

    private val pages = SitePages(baseUrl) { headers["User-Agent"]!! }

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
        val page = pages.get("$baseUrl/comic/$slug", "chapters")
        val details = if (fetchDetails) parseDetails(page.document(baseUrl), slug) else manga
        val chapterList = if (fetchChapters) parseChapters(page.data!!.parseAs(), slug) else chapters
        return SMangaUpdate(details, chapterList)
    }

    private suspend fun parseDetails(document: Document, slug: String): SManga {
        val series = document.jsonLd<ComicSeriesLd> { it.isSeries }
        // The status only lives in the browse catalog; the detail page renders it without a stable hook.
        val catalogEntry = fetchCatalog().firstOrNull { it.slug == slug }

        val genres = series?.genre.orEmpty()
        val adult = genres.any { it.equals("+18", ignoreCase = true) }

        return SManga.create().apply {
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
    }

    private fun parseChapters(chapters: List<SiteChapter>, slug: String): List<SChapter> {
        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED_CHAPTERS, true)
        return chapters
            .filter { !hideLocked || !it.premium }
            .map { chapter ->
                SChapter.create().apply {
                    url = "/comic/$slug/${chapter.slug}"
                    name = buildString {
                        if (chapter.premium) append("\uD83D\uDD12 ")
                        append(chapter.name)
                        if (!chapter.title.isNullOrBlank()) {
                            append(": ", chapter.title)
                        }
                    }
                    date_upload = chapter.createdAt
                }
            }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = pages.get(baseUrl + chapter.url, "pages").data?.parseAs<List<String>>().orEmpty()
        return images.mapIndexed { idx, url ->
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

    private suspend fun fetchCatalog(): List<BrowseSeries> = pages.get("$baseUrl/comics", "catalog").data!!.parseAs()

    private inline fun <reified T> Document.jsonLd(predicate: (T) -> Boolean): T? = select("script[type=application/ld+json]")
        .mapNotNull { runCatching { it.data().parseAs<T>() }.getOrNull() }
        .firstOrNull(predicate)

    private fun Document.synopsis(): String? = selectFirst("#series-synopsis-text")?.let { element ->
        element.select("p").takeIf { it.isNotEmpty() }?.joinToString("\n\n") { it.text() }
            ?: element.text()
    }?.takeIf { it.isNotBlank() }

    companion object {
        private const val PREF_HIDE_LOCKED_CHAPTERS = "pref_hide_locked_chapters"
    }
}
