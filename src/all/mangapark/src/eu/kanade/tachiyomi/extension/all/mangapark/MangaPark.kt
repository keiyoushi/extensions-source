package eu.kanade.tachiyomi.extension.all.mangapark

import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class MangaPark(
    override val lang: String,
    private val siteLang: String = lang,
) : HttpSource(), ConfigurableSource {

    override val name = "MangaPark"

    override val supportsLatest = true

    override val versionId = 2

    private val preference = getPreferences()

    private val domain =
        preference.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT) ?: MIRROR_PREF_DEFAULT

    override val baseUrl = "https://$domain"

    private val apiUrl = "$baseUrl/apo/"

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::siteSettingsInterceptor)
        .addNetworkInterceptor(CookieInterceptor(domain, "nsfw" to "2"))
        .rateLimitHost(apiUrl.toHttpUrl(), 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.POPULAR)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.LATEST)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = GraphQL(
            SearchVariables(
                SearchPayload(
                    page = page,
                    size = size,
                    query = query.takeUnless(String::isEmpty),
                    incGenres = filters.firstInstanceOrNull<GenreFilter>()?.included,
                    excGenres = filters.firstInstanceOrNull<GenreFilter>()?.excluded,
                    incTLangs = listOf(siteLang),
                    incOLangs = filters.firstInstanceOrNull<OriginalLanguageFilter>()?.checked,
                    sortby = filters.firstInstanceOrNull<SortFilter>()?.selected,
                    chapCount = filters.firstInstanceOrNull<ChapterCountFilter>()?.selected,
                    origStatus = filters.firstInstanceOrNull<OriginalStatusFilter>()?.selected,
                    siteStatus = filters.firstInstanceOrNull<UploadStatusFilter>()?.selected,
                ),
            ),
            SEARCH_QUERY,
        ).toJsonRequestBody()

        return POST(apiUrl, headers, payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()

        val entries = result.data.searchComics.items.map { it.data.toSManga() }
        val hasNextPage = entries.size == size

        return MangasPage(entries, hasNextPage)
    }

    private var genreCache: List<Pair<String, String>> = emptyList()
    private var genreFetchAttempt = 0

    private fun getGenres() {
        if (genreCache.isEmpty() && genreFetchAttempt < 3) {
            val elements = runCatching {
                client.newCall(GET("$baseUrl/search", headers)).execute()
                    .use { it.asJsoup() }
                    .select("div.flex-col:contains(Genres) div.whitespace-nowrap")
            }.getOrNull().orEmpty()

            genreCache = elements.mapNotNull {
                val name = it.selectFirst("span.whitespace-nowrap")
                    ?.text()?.takeUnless(String::isEmpty)
                    ?: return@mapNotNull null

                val key = it.attr("q:key")
                    .takeUnless(String::isEmpty) ?: return@mapNotNull null

                Pair(name, key)
            }
            genreFetchAttempt++
        }
    }

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching(::getGenres)
        }

        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            OriginalLanguageFilter(),
            OriginalStatusFilter(),
            UploadStatusFilter(),
            ChapterCountFilter(),
        )

        if (genreCache.isEmpty()) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Press 'reset' to attempt to load genres"),
            )
        } else {
            filters.addAll(1, listOf(GenreFilter(genreCache)))
        }

        return FilterList(filters)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val payload = GraphQL(
            IdVariables(manga.url.substringAfterLast("#")),
            DETAILS_QUERY,
        ).toJsonRequestBody()

        return POST(apiUrl, headers, payload)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<DetailsResponse>()

        return result.data.comic.data.toSManga()
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.substringBeforeLast("#")

    override fun chapterListRequest(manga: SManga): Request {
        val payload = GraphQL(
            IdVariables(manga.url.substringAfterLast("#")),
            CHAPTERS_QUERY,
        ).toJsonRequestBody()

        return POST(apiUrl, headers, payload)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterListResponse>()

        return if (preference.getBoolean(DUPLICATE_CHAPTER_PREF_KEY, false)) {
            result.data.chapterList.flatMap {
                it.data.dupChapters.map { it.data.toSChapter() }
            }.reversed()
        } else {
            result.data.chapterList.map { it.data.toSChapter() }.reversed()
        }
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBeforeLast("#")

    override fun pageListRequest(chapter: SChapter): Request {
        val payload = GraphQL(
            IdVariables(chapter.url.substringAfterLast("#")),
            PAGES_QUERY,
        ).toJsonRequestBody()

        return POST(apiUrl, headers, payload)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageListResponse>()

        return result.data.chapterPages.data.imageFile.urlList.mapIndexed { idx, url ->
            Page(idx, "", url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = "Preferred Mirror"
            entries = mirrors
            entryValues = mirrors
            setDefaultValue(MIRROR_PREF_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Restart Tachiyomi to apply changes", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = DUPLICATE_CHAPTER_PREF_KEY
            title = "Fetch Duplicate Chapters"
            summary = "Refresh chapter list to apply changes"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private inline fun <reified T> Response.parseAs(): T =
        use { body.string() }.let(json::decodeFromString)

    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? =
        filterIsInstance<T>().firstOrNull()

    private inline fun <reified T : Any> T.toJsonRequestBody() =
        json.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private val cookiesNotSet = AtomicBoolean(true)
    private val latch = CountDownLatch(1)

    // sets necessary cookies to not block genres like `Hentai`
    private fun siteSettingsInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val settingsUrl = "$baseUrl/aok/settings-save"

        if (
            request.url.toString() != settingsUrl &&
            request.url.host == domain
        ) {
            if (cookiesNotSet.getAndSet(false)) {
                val payload =
                    """{"data":{"general_autoLangs":[],"general_userLangs":[],"general_excGenres":[],"general_prefLangs":[]}}"""
                        .toRequestBody(JSON_MEDIA_TYPE)

                client.newCall(POST(settingsUrl, headers, payload)).execute().close()

                latch.countDown()
            } else {
                latch.await()
            }
        }

        return chain.proceed(request)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    companion object {
        private const val size = 24
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

        private const val MIRROR_PREF_KEY = "pref_mirror"
        private const val MIRROR_PREF_DEFAULT = "mangapark.net"
        private val mirrors = arrayOf(
            "mangapark.net",
            "mangapark.com",
            "mangapark.org",
            "mangapark.me",
            "mangapark.io",
            "mangapark.to",
            "comicpark.org",
            "comicpark.to",
            "readpark.org",
            "readpark.net",
            "parkmanga.com",
            "parkmanga.net",
            "parkmanga.org",
            "mpark.to",
        )

        private const val DUPLICATE_CHAPTER_PREF_KEY = "pref_dup_chapters"
    }
}
