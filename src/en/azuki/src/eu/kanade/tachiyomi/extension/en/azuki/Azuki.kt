package eu.kanade.tachiyomi.extension.en.azuki

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Azuki :
    HttpSource(),
    ConfigurableSource {
    override val name = "Omoi"
    override val baseUrl = "https://www.omoi.com"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId = 2

    private val apiUrl = "https://production.api.azuki.co"
    private val organizationKey = "199e5a19-a236-49f5-81f4-43d4a541748a"
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if ((response.code == 401 || response.code == 403) && request.url.pathSegments[2].contains("pages") && request.url.host == apiUrl.toHttpUrl().host) {
                throw IOException("Log in via WebView and purchase this chapter to read.")
            }
            if (response.code == 404 && request.url.pathSegments[2].contains("pages") && request.url.host == apiUrl.toHttpUrl().host) {
                throw IOException("This chapter is not available.")
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/discover?sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ol.o-series-card-list li").map {
            SManga.create().apply {
                val link = it.selectFirst("a.a-card-link")!!
                val uuid = link.attr("data-ga-item-id").substringAfter("series-")
                val slug = (link.absUrl("href")).toHttpUrl().pathSegments.last()
                setUrlWithoutDomain("$slug#$uuid")
                title = link.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/discover?sort=recent_series&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/discover".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            filters.firstInstanceOrNull<SortFilter>()?.value?.let {
                addQueryParameter("sort", it)
            }

            filters.firstInstanceOrNull<AccessTypeFilter>()?.value?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("access_type", it)
            }

            filters.firstInstanceOrNull<PublisherFilter>()?.value?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("publisher_slug", it)
            }

            filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.forEach {
                addQueryParameter("tags[]", it.value)
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = "$baseUrl/${manga.url}".toHttpUrl().pathSegments.first()
        return GET("$apiUrl/manga/slug/$slug/v0", apiHeaders())
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl/${manga.url}".toHttpUrl().pathSegments.first()
        return "$baseUrl/series/$slug"
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/${manga.url}".toHttpUrl()
        val slug = url.pathSegments.first()
        val uuid = url.fragment
        val chapterUrl = "$apiUrl/mangas/$uuid/chapters/v4".toHttpUrl().newBuilder()
            .addQueryParameter("order", "ascending")
            .addQueryParameter("count", "1000")
            .fragment(slug)
            .build()
        return GET(chapterUrl, apiHeaders())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val uuid = response.request.url.pathSegments[1]
        val slug = response.request.url.fragment!!
        val result = response.parseAs<ChapterDto>()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)

        val unlockedChapterIds = try {
            val request = GET("$apiUrl/user/mangas/$uuid/v0", apiHeaders())
            val response = client.newCall(request).execute()
            val result = response.parseAs<UserMangaStatusDto>()
            (result.purchasedChapterUuids + result.unlockedChapterUuids).toSet()
        } catch (_: Exception) {
            emptySet()
        }

        return result.chapters.map {
            val now = System.currentTimeMillis()
            val isFree = it.freePublishedDate != null &&
                dateFormat.tryParse(it.freePublishedDate) <= now &&
                (it.freeUnpublishedDate == null || dateFormat.tryParse(it.freeUnpublishedDate) > now)
            val isLocked = it.uuid !in unlockedChapterIds && !isFree
            it to isLocked
        }
            .filter { (_, isLocked) -> !hideLocked || !isLocked }
            .map { (chapter, isLocked) -> chapter.toSChapter(slug, isLocked, dateFormat) }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val slug = url.fragment
        val chapterUuid = url.pathSegments.first()
        return "$baseUrl/series/$slug/read/$chapterUuid"
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUuid = "$baseUrl/${chapter.url}".toHttpUrl().pathSegments.first()
        val url = "$apiUrl/chapters/$chapterUuid/pages/v1"
        return GET(url, apiHeaders())
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageListDto>()
        return result.data.pages.mapIndexed { i, page ->
            val highRes = page.image.webp.maxBy { it.width }
            // This will give the highest possible resolution even if x2400 image doesn't exist.
            val highResUrl = highRes.url.replace(Regex("""/\d+_"""), "/2400_")
            Page(i, imageUrl = "$highResUrl?drm=1")
        }
    }

    private fun apiHeaders(): Headers {
        val token = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "idToken" }?.value

        return headersBuilder()
            .set("azuki-organization-key", organizationKey)
            .apply {
                if (token != null) {
                    set("x-user-token", token)
                }
            }
            .build()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        SortFilter(),
        AccessTypeFilter(),
        PublisherFilter(),
        GenreFilter(),
    )

    // Unsupported
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
