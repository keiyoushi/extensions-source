package eu.kanade.tachiyomi.extension.all.asurascans

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class AsuraScansEn : MangaThemesia(
    "Asura Scans",
    "https://asuratoon.com",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
) {

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val baseUrl by lazy {
        preferences.baseUrlHost.let { "https://$it" }
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::urlChangeInterceptor)
        .addInterceptor(::domainChangeIntercept)
        .rateLimit(1, 3, TimeUnit.SECONDS)
        .build()

    override val seriesDescriptionSelector = "div.desc p, div.entry-content p, div[itemprop=description]:not(:has(p))"
    override val seriesArtistSelector = ".fmed b:contains(artist)+span, .infox span:contains(artist)"
    override val seriesAuthorSelector = ".fmed b:contains(author)+span, .infox span:contains(author)"

    override val pageSelector = "div.rdminimal > img, div.rdminimal > p > img, div.rdminimal > a > img, div.rdminimal > p > a > img, " +
        "div.rdminimal > noscript > img, div.rdminimal > p > noscript > img, div.rdminimal > a > noscript > img, div.rdminimal > p > a > noscript > img"

    // Permanent Url for Manga/Chapter End
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return super.fetchPopularManga(page).tempUrlToPermIfNeeded()
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return super.fetchLatestUpdates(page).tempUrlToPermIfNeeded()
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters).tempUrlToPermIfNeeded()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)
        if (query.isBlank()) return request

        val url = request.url.newBuilder()
            .addPathSegment("page/$page/")
            .removeAllQueryParameters("page")
            .removeAllQueryParameters("title")
            .addQueryParameter("s", query)
            .build()

        return request.newBuilder()
            .url(url)
            .build()
    }

    // Temp Url for manga/chapter
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val newManga = manga.titleToUrlFrag()

        return super.fetchChapterList(newManga)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val newManga = manga.titleToUrlFrag()

        return super.fetchMangaDetails(newManga)
    }

    override fun getMangaUrl(manga: SManga): String {
        val dbSlug = manga.url
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")

        val storedSlug = preferences.slugMap[dbSlug] ?: dbSlug

        return "$baseUrl$mangaUrlDirectory/$storedSlug/"
    }

    // Skip scriptPages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageSelector)
            .filterNot { it.attr("src").isNullOrEmpty() }
            .mapIndexed { i, img -> Page(i, document.location(), img.attr("abs:src")) }
    }

    override fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    private fun Observable<MangasPage>.tempUrlToPermIfNeeded(): Observable<MangasPage> {
        return this.map { mangasPage ->
            MangasPage(
                mangasPage.mangas.map { it.tempUrlToPermIfNeeded() },
                mangasPage.hasNextPage,
            )
        }
    }

    private fun SManga.tempUrlToPermIfNeeded(): SManga {
        if (!preferences.permaUrlPref) return this

        val slugMap = preferences.slugMap

        val sMangaTitleFirstWord = this.title.split(" ")[0]
        if (!this.url.contains("/$sMangaTitleFirstWord", ignoreCase = true)) {
            val currentSlug = this.url
                .removeSuffix("/")
                .substringAfterLast("/")

            val permaSlug = currentSlug.replaceFirst(TEMP_TO_PERM_REGEX, "")

            slugMap[permaSlug] = currentSlug

            this.url = "$mangaUrlDirectory/$permaSlug/"
        }
        preferences.slugMap = slugMap
        return this
    }

    private fun SManga.titleToUrlFrag(): SManga {
        return try {
            this.apply {
                url = "$url#${title.toSearchQuery()}"
            }
        } catch (e: UninitializedPropertyAccessException) {
            // when called from deep link, title is not present
            this
        }
    }

    private fun urlChangeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val frag = request.url.fragment

        if (frag.isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val dbSlug = request.url.toString()
            .substringBefore("#")
            .removeSuffix("/")
            .substringAfterLast("/")

        val slugMap = preferences.slugMap

        val storedSlug = slugMap[dbSlug] ?: dbSlug

        val response = chain.proceed(
            request.newBuilder()
                .url("$baseUrl$mangaUrlDirectory/$storedSlug/")
                .build(),
        )

        if (!response.isSuccessful && response.code == 404) {
            response.close()

            val newSlug = getNewSlug(storedSlug, frag)
                ?: throw IOException("Migrate from Asura to Asura")

            slugMap[dbSlug] = newSlug
            preferences.slugMap = slugMap

            return chain.proceed(
                request.newBuilder()
                    .url("$baseUrl$mangaUrlDirectory/$newSlug/")
                    .build(),
            )
        }

        return response
    }

    private fun getNewSlug(existingSlug: String, frag: String): String? {
        val permaSlug = existingSlug
            .replaceFirst(TEMP_TO_PERM_REGEX, "")

        val search = frag.substringBefore("#")

        val mangas = client.newCall(searchMangaRequest(1, search, FilterList()))
            .execute()
            .use {
                searchMangaParse(it)
            }

        return mangas.mangas.firstOrNull { newManga ->
            newManga.url.contains(permaSlug, true)
        }
            ?.url
            ?.removeSuffix("/")
            ?.substringAfterLast("/")
    }

    private fun String.toSearchQuery(): String {
        return this.trim()
            .lowercase()
            .replace(titleSpecialCharactersRegex, "+")
            .replace(trailingPlusRegex, "")
    }

    private var lastDomain = ""

    private fun domainChangeIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host !in listOf(preferences.baseUrlHost, lastDomain)) {
            return chain.proceed(request)
        }

        if (lastDomain.isNotEmpty()) {
            val newUrl = request.url.newBuilder()
                .host(preferences.baseUrlHost)
                .build()

            return chain.proceed(
                request.newBuilder()
                    .url(newUrl)
                    .build(),
            )
        }

        val response = chain.proceed(request)

        if (request.url.host == response.request.url.host) return response

        response.close()

        preferences.baseUrlHost = response.request.url.host

        lastDomain = request.url.host

        val newUrl = request.url.newBuilder()
            .host(response.request.url.host)
            .build()

        return chain.proceed(
            request.newBuilder()
                .url(newUrl)
                .build(),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_PERM_MANGA_URL_KEY_PREFIX + lang
            title = PREF_PERM_MANGA_URL_TITLE
            summary = PREF_PERM_MANGA_URL_SUMMARY
            setDefaultValue(true)
        }.also(screen::addPreference)

        super.setupPreferenceScreen(screen)
    }

    private val SharedPreferences.permaUrlPref
        get() = getBoolean(PREF_PERM_MANGA_URL_KEY_PREFIX + lang, true)

    private var SharedPreferences.slugMap: MutableMap<String, String>
        get() {
            val serialized = getString(PREF_URL_MAP, null) ?: return mutableMapOf()

            return try {
                json.decodeFromString(serialized)
            } catch (e: Exception) {
                mutableMapOf()
            }
        }
        set(slugMap) {
            val serialized = json.encodeToString(slugMap)
            edit().putString(PREF_URL_MAP, serialized).commit()
        }

    private var SharedPreferences.baseUrlHost
        get() = getString(BASE_URL_PREF, defaultBaseUrlHost) ?: defaultBaseUrlHost
        set(newHost) {
            edit().putString(BASE_URL_PREF, newHost).commit()
        }

    companion object {
        private const val PREF_PERM_MANGA_URL_KEY_PREFIX = "pref_permanent_manga_url_2_"
        private const val PREF_PERM_MANGA_URL_TITLE = "Permanent Manga URL"
        private const val PREF_PERM_MANGA_URL_SUMMARY = "Turns all manga urls into permanent ones."
        private const val PREF_URL_MAP = "pref_url_map"
        private const val BASE_URL_PREF = "pref_base_url_host"
        private const val defaultBaseUrlHost = "asuratoon.com"
        private val TEMP_TO_PERM_REGEX = Regex("""^\d+-""")
        private val titleSpecialCharactersRegex = Regex("""[^a-z0-9]+""")
        private val trailingPlusRegex = Regex("""\++$""")
    }
}
