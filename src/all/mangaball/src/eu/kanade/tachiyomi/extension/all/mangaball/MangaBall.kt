package eu.kanade.tachiyomi.extension.all.mangaball

import android.util.Log
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException

@Source
abstract class MangaBall :
    KeiSource(),
    ConfigurableSource {

    private val siteLang: List<String>
        get() = when (lang) {
            "ar" -> listOf("ar")
            "bg" -> listOf("bg")
            "bn" -> listOf("bn")
            "ca" -> listOf("ca", "ca-ad", "ca-es", "ca-fr", "ca-it", "ca-pt")
            "cs" -> listOf("cs")
            "da" -> listOf("da")
            "de" -> listOf("de")
            "el" -> listOf("el")
            "en" -> listOf("en")
            "es" -> listOf("es", "es-ar", "es-mx", "es-es", "es-la", "es-419")
            "fa" -> listOf("fa")
            "fi" -> listOf("fi")
            "fr" -> listOf("fr")
            "he" -> listOf("he")
            "hi" -> listOf("hi")
            "hu" -> listOf("hu")
            "id" -> listOf("id")
            "it" -> listOf("it", "it-it")
            "is" -> listOf("ib", "ib-is", "is")
            "ja" -> listOf("ja", "jp")
            "ko" -> listOf("ko", "kr")
            "kn" -> listOf("kn", "kn-in", "kn-my", "kn-sg", "kn-tw")
            "ml" -> listOf("ml", "ml-in", "ml-my", "ml-sg", "ml-tw")
            "ms" -> listOf("ms")
            "ne" -> listOf("ne")
            "nl" -> listOf("nl", "nl-be")
            "no" -> listOf("no")
            "pl" -> listOf("pl")
            "pt-BR" -> listOf("pt-br", "pt-pt")
            "ro" -> listOf("ro")
            "ru" -> listOf("ru")
            "sk" -> listOf("sk")
            "sl" -> listOf("sl")
            "sq" -> listOf("sq")
            "sr" -> listOf("sr", "sr-cyrl")
            "sv" -> listOf("sv")
            "ta" -> listOf("ta")
            "th" -> listOf("th", "th-hk", "th-kh", "th-la", "th-my", "th-sg")
            "tr" -> listOf("tr")
            "uk" -> listOf("uk")
            "vi" -> listOf("vi")
            "zh" -> listOf("zh", "zh-cn", "zh-hk", "zh-mo", "zh-sg", "zh-tw", "cn")
            else -> listOf(lang)
        }

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = searchAdvanced(page, "", FilterList(), sortBy = "views", sortOrder = "desc")

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchAdvanced(page, "", FilterList(), sortBy = "lastupdate", sortOrder = "desc")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchAdvanced(page, query, filters)

    private suspend fun searchAdvanced(
        page: Int,
        query: String,
        filters: FilterList,
        sortBy: String? = null,
        sortOrder: String? = null,
    ): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()

        // The API only orders by relevance when sort_by is omitted, which "Relevance" maps to.
        // Keyword-less browse falls back to last-updated so it stays ordered.
        val effectiveSortBy = sortBy ?: sort?.sortBy ?: if (query.isBlank()) "lastupdate" else null

        val url = "$baseUrl/api/v1/title/search-advanced".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
            if (effectiveSortBy != null) {
                addQueryParameter("sort_by", effectiveSortBy)
                addQueryParameter("sort_order", sortOrder ?: sort?.sortOrder ?: "desc")
            }
            addQueryParameter("tag_mode", filters.firstInstanceOrNull<TagModeFilter>()?.selected ?: "AND")
            addQueryParameter("adult_mode", if (hideNsfwPreference()) "no_18" else "all")

            if (query.isNotBlank()) {
                addQueryParameter("keyword", query.trim())
            }

            filters.firstInstanceOrNull<TypeFilter>()?.selected?.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<DemographicFilter>()?.selected?.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("publicationDemographic", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.takeIf { it.isNotEmpty() }
                ?.let { addQueryParameter("status", it) }

            val included = filters.filterIsInstance<TriStateGroupFilter<String>>().flatMap { it.included }
            if (included.isNotEmpty()) {
                addQueryParameter("included_tags", included.joinToString(","))
            }

            val excluded = filters.filterIsInstance<TriStateGroupFilter<String>>().flatMap { it.excluded }
            if (excluded.isNotEmpty()) {
                addQueryParameter("excluded_tags", excluded.joinToString(","))
            }
        }.build()

        return client.get(url, headers).parseAs<SearchResponse>().toMangasPage()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        TypeFilter(),
        DemographicFilter(),
        StatusFilter(),
        TagModeFilter(),
        ContentFilter(),
        FormatFilter(),
        GenreFilter(),
        ThemeFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != LEGACY_HOST) return null

        val id = when (url.pathSegments.firstOrNull()) {
            "title-detail" -> url.pathSegments.getOrNull(1)
            "chapter-detail" -> url.pathSegments.getOrNull(1)
                ?.let { chapterId ->
                    client.get("$baseUrl/api/v1/chapter-detail?chapter_id=$chapterId")
                        .parseAs<ChapterDetailResponse>()
                        .data.chapter.titleId
                }
            else -> null
        } ?: return null

        return getMangaDetails(id)
    }

    override fun getMangaUrl(manga: SManga): String {
        // The slug form only server-renders a stub page, so link to the id form the site itself uses.
        val id = manga.memo.getStringOrNull("id") ?: manga.url
        return "$baseUrl/title-detail/$id"
    }

    private suspend fun getMangaDetails(idOrSlug: String): SManga = client.get("$baseUrl/api/v1/title/detail/$idOrSlug")
        .parseAs<TitleResponse>()
        .data
        .toSManga()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val titleId = manga.memo.getStringOrNull("id")

        return if (fetchChapters && titleId != null) {
            coroutineScope {
                val mangaDeferred = async { if (fetchDetails) getMangaDetails(manga.url) else manga }
                val chaptersDeferred = async { getChapterList(titleId) }

                SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
            }
        } else {
            // Entries saved by older versions have no id in memo, so one details fetch is needed to
            // resolve it. Returning that manga persists the id, saving the fetch on later refreshes.
            val updatedManga = if (titleId == null || fetchDetails) getMangaDetails(manga.url) else manga

            val updatedChapters = if (fetchChapters) {
                getChapterList(updatedManga.memo.getString("id"))
            } else {
                chapters
            }

            SMangaUpdate(updatedManga, updatedChapters)
        }
    }

    private suspend fun getChapterList(titleId: String): List<SChapter> {
        val body = TitleIdRequest(titleId).toJsonBody()

        val chapters = client.post("$baseUrl/api/v1/chapter/chapter-listing-by-title-id", headers, body)
            .parseAs<ChapterListResponse>()
            .data
            .mapNotNull { it.toSChapter(siteLang) }

        preferences.rememberScanlators(chapters.mapNotNull { it.scanlator })

        return chapters.filterBlacklistedScanlators(preferences.scanlatorBlacklist())
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter-detail/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = client.get("$baseUrl/api/v1/chapter-detail?chapter_id=${chapter.url}")
            .parseAs<ChapterDetailResponse>()
            .data.chapter.pages

        updateViews(chapter.url)

        return pages.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun updateViews(chapterId: String) {
        val body = ViewRequest(chapterId, "chapter").toJsonBody()
        val request = POST("$baseUrl/api/v1/views/update", headers, body)

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.closeQuietly()
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(name, "Failed to update views", e)
                    }
                },
            )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = NSFW_PREF
            title = "Hide NSFW content"
            summary = "Hide titles marked as 18+"
            setDefaultValue(false)
        }.also(screen::addPreference)

        screen.addScanlatorBlacklistPreference(preferences)
    }

    private fun hideNsfwPreference() = preferences.getBoolean(NSFW_PREF, false)
}

private const val NSFW_PREF = "nsfw_pref"
private const val LEGACY_HOST = "mangaball.net"

// OkHttp 5 appends "; charset=utf-8" when a request body is built from a String, and this API
// rejects any Content-Type other than a bare "application/json" with HTTP 400.
private inline fun <reified T> T.toJsonBody(): RequestBody = toJsonString().toByteArray().toRequestBody(JSON_MEDIA_TYPE)
