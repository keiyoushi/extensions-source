package eu.kanade.tachiyomi.extension.en.omoi

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import kotlin.time.Instant

@Source
abstract class Azuki :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl = "https://production.api.azuki.co"
    private val organizationKey = "199e5a19-a236-49f5-81f4-43d4a541748a"
    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor {
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
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$baseUrl/discover?sort=popular&page=$page".toHttpUrl())

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$baseUrl/discover?sort=recent_series&page=$page".toHttpUrl())

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        return parseMangaList(url.build())
    }

    private suspend fun parseMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
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

    // Details & Chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val url = "$baseUrl/${manga.url}".toHttpUrl()
        val slug = url.pathSegments.first()

        val details = if (fetchDetails) {
            async { client.get("$apiUrl/manga/slug/$slug/v0", apiHeaders()).parseAs<DetailsDto>().toSManga() }
        } else {
            null
        }
        val chapterList = if (fetchChapters) {
            async { getChapterList(slug, url.fragment!!) }
        } else {
            null
        }

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl/${manga.url}".toHttpUrl().pathSegments.first()
        return "$baseUrl/series/$slug"
    }

    private suspend fun getChapterList(slug: String, uuid: String): List<SChapter> = coroutineScope {
        val chapterUrl = "$apiUrl/mangas/$uuid/chapters/v4".toHttpUrl().newBuilder()
            .addQueryParameter("order", "ascending")
            .addQueryParameter("count", "1000")
            .build()
        val result = async { client.get(chapterUrl, apiHeaders()).parseAs<ChapterDto>() }
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)

        val unlockedChapterIds = try {
            val status = client.get("$apiUrl/user/mangas/$uuid/v0", apiHeaders()).parseAs<UserMangaStatusDto>()
            (status.purchasedChapterUuids + status.unlockedChapterUuids).toSet()
        } catch (_: Exception) {
            emptySet()
        }

        result.await().chapters.map {
            val now = System.currentTimeMillis()
            val isFree = it.freePublishedDate != null &&
                Instant.tryParse(it.freePublishedDate) <= now &&
                (it.freeUnpublishedDate == null || Instant.tryParse(it.freeUnpublishedDate) > now)
            val isLocked = it.uuid !in unlockedChapterIds && !isFree
            it to isLocked
        }
            .filter { (_, isLocked) -> !hideLocked || !isLocked }
            .map { (chapter, isLocked) -> chapter.toSChapter(slug, isLocked) }
            .reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val slug = url.fragment
        val chapterUuid = url.pathSegments.first()
        return "$baseUrl/series/$slug/read/$chapterUuid"
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUuid = "$baseUrl/${chapter.url}".toHttpUrl().pathSegments.first()
        val result = client.get("$apiUrl/chapters/$chapterUuid/pages/v1", apiHeaders()).parseAs<PageListDto>()
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

        return headers.newBuilder()
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
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        SortFilter(),
        AccessTypeFilter(),
        PublisherFilter(),
        GenreFilter(),
    )

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
