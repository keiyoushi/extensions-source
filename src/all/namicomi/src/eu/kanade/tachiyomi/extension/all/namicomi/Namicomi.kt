package eu.kanade.tachiyomi.extension.all.namicomi

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.CoverArtDto
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

abstract class Namicomi(final override val lang: String, private val extLang: String = lang) :
    ConfigurableSource, HttpSource() {

    override val name = "NamiComi"
    override val baseUrl = NamicomiConstants.webUrl
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val helper = NamicomiHelper(lang)

    final override fun headersBuilder() = super.headersBuilder().apply {
        set("Referer", "$baseUrl/")
        set("Origin", baseUrl)
    }

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())

            if (response.code == 402) {
                throw IOException(helper.intl["error_payment_required"])
            }

            return@addNetworkInterceptor response
        }
        .build()

    private fun sortedMangaRequest(page: Int, orderBy: String): Request {
        val url = NamicomiConstants.apiSearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("order[$orderBy]", "desc")
            .addQueryParameter("availableTranslatedLanguages[]", extLang)
            .addQueryParameter("limit", NamicomiConstants.mangaLimit.toString())
            .addQueryParameter("offset", helper.getMangaListOffset(page))
            .addQueryParameter("includes[]", NamicomiConstants.coverArt)
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
        val coverSuffix = preferences.coverQuality

        val mangaList = mangaListDto.data.map { mangaDataDto ->
            val fileName = mangaDataDto.relationships
                .firstInstanceOrNull<CoverArtDto>()
                ?.attributes?.fileName

            helper.createBasicManga(mangaDataDto, fileName, coverSuffix, extLang)
        }

        return MangasPage(mangaList, mangaListDto.hasNextPage)
    }

    // Search manga section

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(NamicomiConstants.prefixIdSearch)) {
            val mangaId = query.removePrefix(NamicomiConstants.prefixIdSearch)

            if (!helper.containsId(mangaId)) {
                throw Exception(helper.intl["invalid_manga_id"])
            }

            // If the query is an ID, return the manga directly
            val url = NamicomiConstants.apiSearchUrl.toHttpUrl().newBuilder()
                .addQueryParameter("ids[]", query.removePrefix(NamicomiConstants.prefixIdSearch))
                .addQueryParameter("includes[]", NamicomiConstants.coverArt)
                .build()

            return GET(url, headers, CacheControl.FORCE_NETWORK)
        }

        val tempUrl = NamicomiConstants.apiSearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("limit", NamicomiConstants.mangaLimit.toString())
            .addQueryParameter("offset", helper.getMangaListOffset(page))
            .addQueryParameter("includes[]", NamicomiConstants.coverArt)

        val actualQuery = query.replace(NamicomiConstants.whitespaceRegex, " ")
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
        "$baseUrl/$extLang" + manga.url + "/${helper.titleToSlug(manga.title)}"

    /**
     * Get the API endpoint URL for the entry details.
     */
    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = (NamicomiConstants.apiUrl + manga.url).toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", NamicomiConstants.coverArt)
            .addQueryParameter("includes[]", NamicomiConstants.organization)
            .addQueryParameter("includes[]", NamicomiConstants.tag)
            .addQueryParameter("includes[]", NamicomiConstants.primaryTag)
            .addQueryParameter("includes[]", NamicomiConstants.secondaryTag)
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
        return paginatedChapterListRequest(helper.getIdFromUrl(manga.url), 0)
    }

    /**
     * Required because the chapter list API endpoint is paginated.
     */
    private fun paginatedChapterListRequest(mangaId: String, offset: Int): Request {
        val url = NamicomiConstants.apiChapterUrl.toHttpUrl().newBuilder()
            .addQueryParameter("titleId", mangaId)
            .addQueryParameter("includes[]", NamicomiConstants.organization)
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
            NamicomiConstants.apiGatingCheckUrl,
            headers,
            chapterIds
                .map { EntityAccessRequestItemDto(it, NamicomiConstants.chapter) }
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
            .substringAfter("${NamicomiConstants.apiMangaUrl}/")

        val chapterListResponse = response.parseAs<ChapterListDto>()
        val chapterListResults = chapterListResponse.data.toMutableList()
        var offset = chapterListResponse.offset
        var hasNextPage = chapterListResponse.hasNextPage

        // Max results that can be returned is 500 so need to make more API
        // calls if the chapter list response has a next page.
        while (hasNextPage) {
            offset += chapterListResponse.limit

            val newRequest = paginatedChapterListRequest(mangaId, offset)
            val newResponse = client.newCall(newRequest).execute()
            val newChapterList = newResponse.parseAs<ChapterListDto>()
            chapterListResults.addAll(newChapterList.data)

            hasNextPage = newChapterList.hasNextPage
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
            val isAccessible = accessibleChapterMap.getOrElse(it.id) { false }
            when {
                // Chapter can be viewed
                isAccessible -> helper.createChapter(it, extLang)
                // Chapter cannot be viewed and user wants to see locked chapters
                preferences.showLockedChapters -> {
                    helper.createChapter(it, extLang).apply {
                        name = helper.intl.format("chapter_locked", name)
                    }
                }
                // Ignore locked chapters otherwise
                else -> null
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/chapter/")
        val url = "${NamicomiConstants.apiUrl}/images/chapter/$chapterId?newQualities=true"
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
            key = NamicomiConstants.getCoverQualityPreferenceKey(extLang)
            title = helper.intl["cover_quality"]
            entries = NamicomiConstants.getCoverQualityPreferenceEntries(helper.intl)
            entryValues = NamicomiConstants.getCoverQualityPreferenceEntryValues()
            setDefaultValue(NamicomiConstants.getCoverQualityPreferenceDefaultValue())
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String

                preferences.edit()
                    .putString(NamicomiConstants.getCoverQualityPreferenceKey(extLang), entry)
                    .commit()
            }
        }

        val dataSaverPref = SwitchPreferenceCompat(screen.context).apply {
            key = NamicomiConstants.getDataSaverPreferenceKey(extLang)
            title = helper.intl["data_saver"]
            summary = helper.intl["data_saver_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(NamicomiConstants.getDataSaverPreferenceKey(extLang), checkValue)
                    .commit()
            }
        }

        val showLockedChaptersPref = SwitchPreferenceCompat(screen.context).apply {
            key = NamicomiConstants.getShowLockedChaptersPreferenceKey(extLang)
            title = helper.intl["show_locked_chapters"]
            summary = helper.intl["show_locked_chapters_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(NamicomiConstants.getShowLockedChaptersPreferenceKey(extLang), checkValue)
                    .commit()
            }
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

    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? =
        firstOrNull { it is T } as? T?

    private val SharedPreferences.coverQuality
        get() = getString(NamicomiConstants.getCoverQualityPreferenceKey(extLang), "")

    private val SharedPreferences.useDataSaver
        get() = getBoolean(NamicomiConstants.getDataSaverPreferenceKey(extLang), false)

    private val SharedPreferences.showLockedChapters
        get() = getBoolean(NamicomiConstants.getShowLockedChaptersPreferenceKey(extLang), false)
}
