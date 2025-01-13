package eu.kanade.tachiyomi.extension.all.namicomi

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityAccessMapDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityAccessRequestDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityAccessRequestItemDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.MangaListDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.PageListDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class NamiComi(final override val lang: String, private val extLang: String = lang) :
    ConfigurableSource, HttpSource() {

    override val name = "NamiComi"
    override val baseUrl = NamiComiConstants.webUrl
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val helper = NamiComiHelper(lang)

    final override fun headersBuilder() = super.headersBuilder().apply {
        set("Referer", "$baseUrl/")
        set("Origin", baseUrl)
    }

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())

            if (response.code == 402) {
                response.close()
                throw IOException(helper.intl["error_payment_required"])
            }

            return@addNetworkInterceptor response
        }
        .build()

    private fun sortedMangaRequest(page: Int, orderBy: String): Request {
        val url = NamiComiConstants.apiSearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("order[$orderBy]", "desc")
            .addQueryParameter("availableTranslatedLanguages[]", extLang)
            .addQueryParameter("limit", NamiComiConstants.mangaLimit.toString())
            .addQueryParameter("offset", helper.getMangaListOffset(page))
            .addQueryParameter("includes[]", NamiComiConstants.coverArt)
            .addQueryParameter("includes[]", NamiComiConstants.primaryTag)
            .addQueryParameter("includes[]", NamiComiConstants.secondaryTag)
            .addQueryParameter("includes[]", NamiComiConstants.tag)
            .build()

        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    // Popular manga section

    override fun popularMangaRequest(page: Int): Request =
        sortedMangaRequest(page, "views")

    override fun popularMangaParse(response: Response): MangasPage =
        mangaListParse(response)

    // Latest manga section

    override fun latestUpdatesRequest(page: Int): Request =
        sortedMangaRequest(page, "publishedAt")

    override fun latestUpdatesParse(response: Response): MangasPage =
        mangaListParse(response)

    private fun mangaListParse(response: Response): MangasPage {
        if (response.code == 204) {
            return MangasPage(emptyList(), false)
        }

        val mangaListDto = response.parseAs<MangaListDto>()
        val mangaList = mangaListDto.data.map { mangaDataDto ->
            helper.createManga(
                mangaDataDto,
                extLang,
                preferences.coverQuality,
            )
        }

        return MangasPage(mangaList, mangaListDto.meta.hasNextPage)
    }

    // Search manga section

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(NamiComiConstants.prefixIdSearch)) {
            val mangaId = query.removePrefix(NamiComiConstants.prefixIdSearch)

            if (mangaId.isEmpty()) {
                throw Exception(helper.intl["invalid_manga_id"])
            }

            // If the query is an ID, return the manga directly
            val url = NamiComiConstants.apiSearchUrl.toHttpUrl().newBuilder()
                .addQueryParameter("ids[]", query.removePrefix(NamiComiConstants.prefixIdSearch))
                .addQueryParameter("includes[]", NamiComiConstants.coverArt)
                .build()

            return GET(url, headers, CacheControl.FORCE_NETWORK)
        }

        val tempUrl = NamiComiConstants.apiSearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("limit", NamiComiConstants.mangaLimit.toString())
            .addQueryParameter("offset", helper.getMangaListOffset(page))
            .addQueryParameter("includes[]", NamiComiConstants.coverArt)

        val actualQuery = query.replace(NamiComiConstants.whitespaceRegex, " ")
        if (actualQuery.isNotBlank()) {
            tempUrl.addQueryParameter("title", actualQuery)
        }

        val finalUrl = helper.filters.addFiltersToUrl(
            url = tempUrl,
            filters = filters.ifEmpty { getFilterList() },
            extLang = extLang,
        )

        return GET(finalUrl, headers, CacheControl.FORCE_NETWORK)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Manga Details section

    override fun getMangaUrl(manga: SManga): String =
        "$baseUrl/$extLang/title/${manga.url}/${helper.titleToSlug(manga.title)}"

    /**
     * Get the API endpoint URL for the entry details.
     */
    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = (NamiComiConstants.apiMangaUrl + manga.url).toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", NamiComiConstants.coverArt)
            .addQueryParameter("includes[]", NamiComiConstants.organization)
            .addQueryParameter("includes[]", NamiComiConstants.tag)
            .addQueryParameter("includes[]", NamiComiConstants.primaryTag)
            .addQueryParameter("includes[]", NamiComiConstants.secondaryTag)
            .build()

        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.parseAs<MangaDto>()

        return helper.createManga(
            manga.data!!,
            extLang,
            preferences.coverQuality,
        )
    }

    // Chapter list section

    /**
     * Get the API endpoint URL for the first page of chapter list.
     */
    override fun chapterListRequest(manga: SManga): Request {
        return paginatedChapterListRequest(manga.url, 0)
    }

    /**
     * Required because the chapter list API endpoint is paginated.
     */
    private fun paginatedChapterListRequest(mangaId: String, offset: Int): Request {
        val url = NamiComiConstants.apiChapterUrl.toHttpUrl().newBuilder()
            .addQueryParameter("titleId", mangaId)
            .addQueryParameter("includes[]", NamiComiConstants.organization)
            .addQueryParameter("limit", "500")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("translatedLanguages[]", extLang)
            .addQueryParameter("order[volume]", "desc")
            .addQueryParameter("order[chapter]", "desc")
            .toString()

        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    /**
     * Requests information about gated chapters (requiring payment & login).
     */
    private fun accessibleChapterListRequest(chapterIds: List<String>): Request {
        return POST(
            NamiComiConstants.apiGatingCheckUrl,
            headers,
            chapterIds
                .map { EntityAccessRequestItemDto(it, NamiComiConstants.chapter) }
                .let { helper.json.encodeToString(EntityAccessRequestDto(it)) }
                .toRequestBody(),
            CacheControl.FORCE_NETWORK,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.code == 204) {
            return emptyList()
        }

        val mangaId = response.request.url.toString()
            .substringBefore("/chapter")
            .substringAfter("${NamiComiConstants.apiMangaUrl}/")

        val chapterListResponse = response.parseAs<ChapterListDto>()
        val chapterListResults = chapterListResponse.data.toMutableList()
        var offset = chapterListResponse.meta.offset
        var hasNextPage = chapterListResponse.meta.hasNextPage

        // Max results that can be returned is 500 so need to make more API
        // calls if the chapter list response has a next page.
        while (hasNextPage) {
            offset += chapterListResponse.meta.limit

            val newRequest = paginatedChapterListRequest(mangaId, offset)
            val newResponse = client.newCall(newRequest).execute()
            val newChapterList = newResponse.parseAs<ChapterListDto>()
            chapterListResults.addAll(newChapterList.data)

            hasNextPage = newChapterList.meta.hasNextPage
        }

        // If there are no chapters, don't attempt to check gating
        if (chapterListResults.isEmpty()) {
            return emptyList()
        }

        val gatingCheckRequest = accessibleChapterListRequest(chapterListResults.map { it.id })
        val gatingCheckResponse = client.newCall(gatingCheckRequest).execute()
        val accessibleChapterMap = gatingCheckResponse.parseAs<EntityAccessMapDto>()
            .data?.attributes?.map ?: emptyMap()

        return chapterListResults.mapNotNull {
            val isAccessible = accessibleChapterMap[it.id]!!
            when {
                // Chapter can be viewed
                isAccessible -> helper.createChapter(it)
                // Chapter cannot be viewed and user wants to see locked chapters
                preferences.showLockedChapters -> {
                    helper.createChapter(it).apply {
                        name = "${NamiComiConstants.lockSymbol} $name"
                    }
                }
                // Ignore locked chapters otherwise
                else -> null
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String =
        "$baseUrl/$extLang/chapter/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url
        val url = "${NamiComiConstants.apiUrl}/images/chapter/$chapterId?newQualities=true"
        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterId = response.request.url.pathSegments.last()
        val pageListDataDto = response.parseAs<PageListDto>().data ?: return emptyList()

        val hash = pageListDataDto.hash
        val prefix = "${pageListDataDto.baseUrl}/chapter/$chapterId/$hash"

        val urls = if (preferences.useDataSaver) {
            pageListDataDto.low.map { prefix + "/low/${it.filename}" }
        } else {
            pageListDataDto.source.map { prefix + "/source/${it.filename}" }
        }

        return urls.mapIndexed { index, url ->
            Page(index, url, url)
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val coverQualityPref = ListPreference(screen.context).apply {
            key = NamiComiConstants.getCoverQualityPreferenceKey(extLang)
            title = helper.intl["cover_quality"]
            entries = NamiComiConstants.getCoverQualityPreferenceEntries(helper.intl)
            entryValues = NamiComiConstants.getCoverQualityPreferenceEntryValues()
            setDefaultValue(NamiComiConstants.getCoverQualityPreferenceDefaultValue())
            summary = "%s"
        }

        val dataSaverPref = SwitchPreferenceCompat(screen.context).apply {
            key = NamiComiConstants.getDataSaverPreferenceKey(extLang)
            title = helper.intl["data_saver"]
            summary = helper.intl["data_saver_summary"]
            setDefaultValue(false)
        }

        val showLockedChaptersPref = SwitchPreferenceCompat(screen.context).apply {
            key = NamiComiConstants.getShowLockedChaptersPreferenceKey(extLang)
            title = helper.intl["show_locked_chapters"]
            summary = helper.intl["show_locked_chapters_summary"]
            setDefaultValue(false)
        }

        screen.addPreference(coverQualityPref)
        screen.addPreference(dataSaverPref)
        screen.addPreference(showLockedChaptersPref)
    }

    override fun getFilterList(): FilterList =
        helper.filters.getFilterList(helper.intl)

    private inline fun <reified T> Response.parseAs(): T = use {
        helper.json.decodeFromString(body.string())
    }

    private val SharedPreferences.coverQuality
        get() = getString(NamiComiConstants.getCoverQualityPreferenceKey(extLang), "")

    private val SharedPreferences.useDataSaver
        get() = getBoolean(NamiComiConstants.getDataSaverPreferenceKey(extLang), false)

    private val SharedPreferences.showLockedChapters
        get() = getBoolean(NamiComiConstants.getShowLockedChaptersPreferenceKey(extLang), false)
}
