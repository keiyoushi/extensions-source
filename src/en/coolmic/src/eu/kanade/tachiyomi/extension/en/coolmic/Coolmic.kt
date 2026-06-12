package eu.kanade.tachiyomi.extension.en.coolmic

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Coolmic :
    HttpSource(),
    ConfigurableSource {
    override val name = "Coolmic"
    private val domain = "coolmic.me"
    override val baseUrl = "https://$domain"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/v1"
    private val cdnUrl = "https://en-img.$domain"
    private val searchUrl = "https://en-search.$domain"
    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override val client = network.client.newBuilder()
        .addInterceptor(CookieInterceptor(domain, "is_mature" to "true"))
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 3 }))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter().apply { state = 1 }))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val keywords = query.trim().split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .joinToString(" ") { "($it|$it*)" }
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value

        val url = "$searchUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", keywords.ifBlank { "(matchall)" })
            .addQueryParameter("size", SEARCH_SIZE.toString())
            .addQueryParameter("start", ((page - 1) * SEARCH_SIZE).toString())
            .addQueryParameter("q.options", "")
            .addQueryParameter("q.parser", if (keywords.isBlank()) "structured" else "simple")
            .addQueryParameter("return", "_all_fields")
            .addEncodedQueryParameter("sort", sort)
            .addQueryParameter("fq", if (status.orEmpty().isEmpty()) "" else "(and (term field=$status))")
            .build()
        return GET(url, headers)
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SeriesResponse>()
        val mangas = result.hits.hit.map { it.fields.toSManga(cdnUrl) }
        return MangasPage(mangas, result.hits.hasNextPage())
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/titles/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parsePageObjects().title.toSManga()

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        return response.parsePageObjects().episodes
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }
            .reversed()
    }

    private fun Response.parsePageObjects(): DetailsResponse = asJsoup().selectFirst("title-page")!!.attr(":page-objects").parseAs()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/episodes/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/viewer/comic/secure_episodes/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ViewerResponse>()
        if (result.imageData.isNullOrEmpty()) throw Exception("Log in via WebView and purchase this chapter to read.")
        return result.imageData.map {
            Page(it.num - 1, it.path)
        }
    }

    override fun imageUrlParse(response: Response): String {
        val page = response.parseAs<PageResponse>()
        val key = fetchKey(page.kmsEncryptedDataKey, page.fileName)
        val url = response.request.url.newBuilder().fragment("key=$key").build().toString()
        return url
    }

    private fun fetchKey(encryptedKey: String, fileName: String): String {
        var response = requestKey(encryptedKey, fileName, csrfToken())
        if (!response.isSuccessful) {
            response.close()
            response = requestKey(encryptedKey, fileName, csrfToken(refresh = true))
        }
        return response.parseAs<KeyResponse>().decryptedKey
    }

    private fun requestKey(encryptedKey: String, fileName: String, token: String): Response {
        val newHeaders = headersBuilder()
            .set("Origin", baseUrl)
            .set("X-CSRF-TOKEN", token)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
        return client.newCall(
            POST(
                "$apiUrl/decryption_keys",
                newHeaders,
                KeyRequestBody(encryptedKey, fileName).toJsonRequestBody(),
            ),
        ).execute()
    }

    private var cachedCsrfToken: String? = null

    @Synchronized
    private fun csrfToken(refresh: Boolean = false): String {
        if (refresh) cachedCsrfToken = null
        return cachedCsrfToken ?: client.newCall(GET(baseUrl, headers)).execute().asJsoup()
            .selectFirst("meta[name=csrf-token]")!!
            .attr("content")
            .also { cachedCsrfToken = it }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        const val SEARCH_SIZE = 20
    }
}
