package eu.kanade.tachiyomi.extension.all.mangadex

import android.content.SharedPreferences
import android.os.Build
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AggregateDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AggregateVolume
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CoverArtDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CoverArtListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDataDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaListDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.Date
import kotlin.collections.orEmpty
import kotlin.collections.toMutableSet
import kotlin.getValue

/**
 * Wrapper class for compatibility with Komikku
 */
@Source
abstract class MangaDex :
    HttpSource(),
    ConfigurableSource {

    private val delegate = object : MangaDexImpl() {
        override val name: String get() = this@MangaDex.name
        override val lang: String get() = this@MangaDex.lang
        override val baseUrl: String get() = this@MangaDex.baseUrl
        override val id: Long get() = this@MangaDex.id
    }

    @Suppress("unused")
    val helper: Helper get() = delegate.helper

    override fun getHomeUrl(): String = delegate.getHomeUrl()

    override val supportsLatest get() = delegate.supportsLatest

    override val client get() = delegate.client

    override fun toString() = delegate.toString()

    override suspend fun getPopularManga(page: Int): MangasPage = delegate.getPopularManga(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = delegate.getLatestUpdates(page)

    /**
     * Komikku calls [latestUpdatesRequest] and [latestUpdatesParse] for MangaDex instead of [getLatestUpdates]
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun latestUpdatesRequest(page: Int): Request = delegate.delegateLatestUpdatesRequest(page)

    /**
     * Komikku calls [latestUpdatesRequest] and [latestUpdatesParse] for MangaDex instead of [getLatestUpdates]
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun latestUpdatesParse(response: Response): MangasPage = delegate.delegateLatestUpdatesParse(response)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = delegate.getSearchManga(
        page,
        query,
        filters,
    )

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = delegate.getMangaUpdate(manga, chapters, fetchDetails, fetchChapters)

    override suspend fun getPageList(chapter: SChapter): List<Page> = delegate.getPageList(chapter)

    override suspend fun getImageUrl(page: Page): String = delegate.getImageUrl(page)

    override fun getMangaUrl(manga: SManga): String = delegate.getMangaUrl(manga)

    override fun getChapterUrl(chapter: SChapter): String = delegate.getChapterUrl(chapter)

    override fun imageRequest(page: Page): Request = delegate.delegateImageRequest(page)

    override fun setupPreferenceScreen(screen: PreferenceScreen) = delegate.setupPreferenceScreen(screen)
}

private abstract class MangaDexImpl :
    KeiSource(),
    ConfigurableSource {

    private val dexLang: String
        get() = when (lang) {
            "zh-Hans" -> "zh"
            "zh-Hant" -> "zh-hk"
            "fil" -> "tl"
            "pt-BR" -> "pt-br"
            "es-419" -> "es-la"
            else -> lang
        }

    private val preferences by getPreferencesLazy { sanitizeExistingUuidPrefs() }

    val helper = Helper(lang)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        val extraHeader = "Android/${Build.VERSION.RELEASE} " +
            "Tachiyomi/${AppInfo.getVersionName()} " +
            "MangaDex/${BuildConfig.VERSION_NAME} " +
            "Keiyoushi"

        set("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
        set("Extra", extraHeader)
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    private suspend fun parseMangasPage(response: Response): MangasPage {
        if (response.code == 204) {
            return MangasPage(emptyList(), false)
        }

        val mangaListDto = response.parseAs<MangaListDto>()

        val coverSuffix = preferences.coverQuality
        val firstVolumeCovers = fetchFirstVolumeCovers(mangaListDto.data).orEmpty()

        val mangaList = mangaListDto.data.map { mangaDataDto ->
            val fileName = firstVolumeCovers.getOrElse(mangaDataDto.id) {
                mangaDataDto.relationships
                    .firstInstanceOrNull<CoverArtDto>()
                    ?.attributes?.fileName
            }
            helper.createBasicManga(mangaDataDto, fileName, coverSuffix, dexLang, preferences.preferExtensionLangTitle)
        }

        return MangasPage(mangaList, mangaListDto.hasNextPage)
    }

    // Popular manga section

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(
        client.get(
            url = Constants.API_MANGA_URL.toHttpUrl().newBuilder()
                .addQueryParameter("order[followedCount]", "desc")
                .addQueryParameter("availableTranslatedLanguage[]", dexLang)
                .addQueryParameter("limit", Constants.MANGA_LIMIT.toString())
                .addQueryParameter("offset", helper.getMangaListOffset(page))
                .addQueryParameter("includes[]", Constants.COVER_ART)
                .addQueryParameter("contentRating[]", preferences.contentRating)
                .addQueryParameter("originalLanguage[]", preferences.originalLanguages)
                .build(),
            cacheControl = CacheControl.FORCE_NETWORK,
        ),
    )

    // Latest manga section

    /**
     * The API endpoint can't sort by date yet, so not implemented.
     */
    private suspend fun parseLatestUpdates(response: Response): MangasPage {
        val chapterListDto = response.parseAs<ChapterListDto>()

        val mangaIds = chapterListDto.data
            .asSequence()
            .flatMap { it.relationships }
            .filterIsInstance<MangaDataDto>()
            .map { it.id }
            .distinct()
            .toSet()

        val mangaApiUrl = Constants.API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", Constants.COVER_ART)
            .addQueryParameter("limit", mangaIds.size.toString())
            .addQueryParameter("contentRating[]", preferences.contentRating)
            .addQueryParameter("ids[]", mangaIds)
            .build()

        val mangaResponse = client.get(
            url = mangaApiUrl,
            cacheControl = CacheControl.FORCE_NETWORK,
        )
        val mangaListDto = mangaResponse.parseAs<MangaListDto>()
        val firstVolumeCovers = fetchFirstVolumeCovers(mangaListDto.data).orEmpty()

        val mangaDtoMap = mangaListDto.data.associateBy({ it.id }, { it })

        val coverSuffix = preferences.coverQuality

        val mangaList = mangaIds.mapNotNull { mangaDtoMap[it] }.map { mangaDataDto ->
            val fileName = firstVolumeCovers.getOrElse(mangaDataDto.id) {
                mangaDataDto.relationships
                    .firstInstanceOrNull<CoverArtDto>()
                    ?.attributes?.fileName
            }
            helper.createBasicManga(mangaDataDto, fileName, coverSuffix, dexLang, preferences.preferExtensionLangTitle)
        }

        return MangasPage(mangaList, chapterListDto.hasNextPage)
    }

    private fun latestUpdatesUrl(page: Int) = Constants.API_CHAPTER_URL.toHttpUrl().newBuilder()
        .addQueryParameter("offset", helper.getLatestChapterOffset(page))
        .addQueryParameter("limit", Constants.LATEST_CHAPTER_LIMIT.toString())
        .addQueryParameter("translatedLanguage[]", dexLang)
        .addQueryParameter("order[publishAt]", "desc")
        .addQueryParameter("includeFutureUpdates", "0")
        .addQueryParameter("originalLanguage[]", preferences.originalLanguages)
        .addQueryParameter("contentRating[]", preferences.contentRating)
        .addQueryParameter(
            "excludedGroups[]",
            Constants.defaultBlockedGroups + preferences.blockedGroups,
        )
        .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
        .addQueryParameter("includeFuturePublishAt", "0")
        .addQueryParameter("includeEmptyPages", "0")
        .build()

    fun delegateLatestUpdatesRequest(page: Int): Request = GET(latestUpdatesUrl(page), headers, CacheControl.FORCE_NETWORK)

    fun delegateLatestUpdatesParse(response: Response): MangasPage = runBlocking { parseLatestUpdates(response) }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseLatestUpdates(client.get(latestUpdatesUrl(page), headers, CacheControl.FORCE_NETWORK))

    // Search manga section

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.endsWith("mangadex.org")) {
            val searchPrefix = url.pathSegments.firstOrNull()?.let { Constants.pathToSearchPrefix[it] }
            if (searchPrefix != null) {
                val match = Constants.uuidRegex.find(url.toString())
                if (match != null) {
                    return getSearchMangaList(page, searchPrefix + match.value, FilterList())
                }
            }
        }
        return getSearchMangaList(page, url.toString(), FilterList())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = when {
        query.startsWith(Constants.PREFIX_CH_SEARCH) ->
            client
                .get(
                    url = searchMangaUrl(
                        page = page,
                        query = Constants.PREFIX_ID_SEARCH + getMangaIdFromChapterId(query.removePrefix(Constants.PREFIX_CH_SEARCH)),
                        filters = filters,
                    ),
                    cacheControl = CacheControl.FORCE_NETWORK,
                )
                .let { parseMangasPage(it) }

        query.startsWith(Constants.PREFIX_USER_SEARCH) ->
            client
                .get(
                    url = searchMangaUploaderUrl(
                        page = page,
                        uploader = query.removePrefix(Constants.PREFIX_USER_SEARCH),
                    ),
                    cacheControl = CacheControl.FORCE_NETWORK,
                )
                .let { parseLatestUpdates(it) }

        query.startsWith(Constants.PREFIX_LIST_SEARCH) ->
            client
                .get(
                    url = searchMangaListUrl(
                        list = query.removePrefix(Constants.PREFIX_LIST_SEARCH),
                    ),
                    cacheControl = CacheControl.FORCE_NETWORK,
                )
                .let { parseSearchMangaList(it, page, filters) }

        else ->
            client
                .get(
                    url = searchMangaUrl(
                        page = page,
                        query = query.trim(),
                        filters = filters,
                    ),
                    cacheControl = CacheControl.FORCE_NETWORK,
                )
                .let { parseMangasPage(it) }
    }

    private suspend fun getMangaIdFromChapterId(id: String): String = client.get(
        url = "${Constants.API_CHAPTER_URL}/$id",
        ensureSuccess = false,
    )
        .let { response ->
            if (!response.isSuccessful) {
                throw Exception(helper.intl.format("unable_to_process_chapter_request", response.code))
            }

            response.parseAs<ChapterDto>().data!!.relationships
                .firstInstanceOrNull<MangaDataDto>()!!.id
        }

    private fun searchMangaUrl(page: Int, query: String, filters: FilterList): HttpUrl {
        if (query.startsWith(Constants.PREFIX_ID_SEARCH)) {
            val mangaId = query.removePrefix(Constants.PREFIX_ID_SEARCH)

            if (!helper.containsUuid(mangaId)) {
                throw Exception(helper.intl["invalid_manga_id"])
            }

            return Constants.API_MANGA_URL.toHttpUrl().newBuilder()
                .addQueryParameter("ids[]", query.removePrefix(Constants.PREFIX_ID_SEARCH))
                .addQueryParameter("includes[]", Constants.COVER_ART)
                .addQueryParameter("contentRating[]", Constants.allContentRatings)
                .build()
        }

        val tempUrl = Constants.API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("limit", Constants.MANGA_LIMIT.toString())
            .addQueryParameter("offset", helper.getMangaListOffset(page))
            .addQueryParameter("includes[]", Constants.COVER_ART)

        when {
            query.startsWith(Constants.PREFIX_GRP_SEARCH) -> {
                val groupId = query.removePrefix(Constants.PREFIX_GRP_SEARCH)

                if (!helper.containsUuid(groupId)) {
                    throw Exception(helper.intl["invalid_group_id"])
                }

                tempUrl.addQueryParameter("group", groupId)
            }

            query.startsWith(Constants.PREFIX_AUTHOR_SEARCH) -> {
                val authorId = query.removePrefix(Constants.PREFIX_AUTHOR_SEARCH)

                if (!helper.containsUuid(authorId)) {
                    throw Exception(helper.intl["invalid_author_id"])
                }

                tempUrl.addQueryParameter("authorOrArtist", authorId)
            }

            else -> {
                val actualQuery = query.replace(Constants.whitespaceRegex, " ")

                if (actualQuery.isNotBlank()) {
                    tempUrl.addQueryParameter("title", actualQuery)
                }
            }
        }

        val finalUrl = helper.mdFilters.addFiltersToUrl(
            url = tempUrl,
            filters = filters.ifEmpty { getFilterList() },
            dexLang = dexLang,
        )

        return finalUrl
    }

    private fun searchMangaListUrl(list: String): HttpUrl = "${Constants.API_LIST_URL}/$list".toHttpUrl()

    private suspend fun parseSearchMangaList(response: Response, page: Int, filters: FilterList): MangasPage {
        val listDto = response.parseAs<ListDto>()
        val listDtoFiltered = listDto.data!!.relationships.filterIsInstance<MangaDataDto>()
        val amount = listDtoFiltered.count()

        if (amount < 1) {
            throw Exception(helper.intl["no_series_in_list"])
        }

        val minIndex = (page - 1) * Constants.MANGA_LIMIT

        val tempUrl = Constants.API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("limit", Constants.MANGA_LIMIT.toString())
            .addQueryParameter("offset", "0")
            .addQueryParameter("includes[]", Constants.COVER_ART)

        val ids = listDtoFiltered
            .filterIndexed { i, _ -> i >= minIndex && i < (minIndex + Constants.MANGA_LIMIT) }
            .map(MangaDataDto::id)
            .toSet()

        tempUrl.addQueryParameter("ids[]", ids)

        val finalUrl = helper.mdFilters.addFiltersToUrl(
            url = tempUrl,
            filters = filters.ifEmpty { getFilterList() },
            dexLang = dexLang,
        )

        val mangaResponse = client.get(
            url = finalUrl,
            cacheControl = CacheControl.FORCE_NETWORK,
        )
        val mangaList = parseSearchMangaList(mangaResponse)

        val hasNextPage = amount.toFloat() / Constants.MANGA_LIMIT - (page.toFloat() - 1) > 1 &&
            ids.size == Constants.MANGA_LIMIT

        return MangasPage(mangaList, hasNextPage)
    }

    private suspend fun parseSearchMangaList(response: Response): List<SManga> {
        val mangaListDto = response.parseAs<MangaListDto>()
        val firstVolumeCovers = fetchFirstVolumeCovers(mangaListDto.data).orEmpty()

        val coverSuffix = preferences.coverQuality

        return mangaListDto.data.map { mangaDataDto ->
            val fileName = firstVolumeCovers.getOrElse(mangaDataDto.id) {
                mangaDataDto.relationships
                    .firstInstanceOrNull<CoverArtDto>()
                    ?.attributes?.fileName
            }
            helper.createBasicManga(mangaDataDto, fileName, coverSuffix, dexLang, preferences.preferExtensionLangTitle)
        }
    }

    private fun searchMangaUploaderUrl(page: Int, uploader: String): HttpUrl = Constants.API_CHAPTER_URL.toHttpUrl().newBuilder()
        .addQueryParameter("offset", helper.getLatestChapterOffset(page))
        .addQueryParameter("limit", Constants.LATEST_CHAPTER_LIMIT.toString())
        .addQueryParameter("translatedLanguage[]", dexLang)
        .addQueryParameter("order[publishAt]", "desc")
        .addQueryParameter("includeFutureUpdates", "0")
        .addQueryParameter("includeFuturePublishAt", "0")
        .addQueryParameter("includeEmptyPages", "0")
        .addQueryParameter("uploader", uploader)
        .addQueryParameter("originalLanguage[]", preferences.originalLanguages)
        .addQueryParameter("contentRating[]", preferences.contentRating)
        .addQueryParameter(
            "excludedGroups[]",
            Constants.defaultBlockedGroups + preferences.blockedGroups,
        )
        .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
        .build()

    // Manga Details section

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.replace("/manga/", "/title/") + "/" + helper.titleToSlug(manga.title)

    /**
     * Get the API endpoint URL for the entry details.
     *
     * @throws Exception if the url is the old format so people migrate
     */
    override suspend fun getMangaByUrl(url: HttpUrl): SManga {
        if (!helper.containsUuid(url.toString())) {
            throw Exception(helper.intl["migrate_warning"])
        }

        val response = client.get(
            url = "${Constants.API_URL}/manga/${url.pathSegments[1]}"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("includes[]", Constants.COVER_ART)
                .addQueryParameter("includes[]", Constants.AUTHOR)
                .addQueryParameter("includes[]", Constants.ARTIST)
                .build(),
            cacheControl = CacheControl.FORCE_NETWORK,
        )

        val manga = response.parseAs<MangaDto>()

        return helper.createManga(
            manga.data!!,
            fetchSimpleChapterList(manga, dexLang),
            fetchFirstVolumeCover(manga),
            dexLang,
            preferences.coverQuality,
            preferences.altTitlesInDesc,
            preferences.preferExtensionLangTitle,
            preferences.finalChapterInDesc,
        )
    }

    /**
     * Get a quick-n-dirty list of the chapters to be used in determining the manga status.
     * Uses the 'aggregate' endpoint.
     *
     * @see Helper.getPublicationStatus
     * @see AggregateDto
     */
    private suspend fun fetchSimpleChapterList(manga: MangaDto, langCode: String): Map<String, AggregateVolume> {
        val url = "${Constants.API_MANGA_URL}/${manga.data!!.id}/aggregate?translatedLanguage[]=$langCode"
        val response = client.get(url)

        return runCatching { response.parseAs<AggregateDto>() }
            .getOrNull()?.volumes.orEmpty()
    }

    /**
     * Attempt to get the first volume cover if the setting is enabled.
     * Uses the 'covers' endpoint.
     *
     * @see CoverArtListDto
     */
    private suspend fun fetchFirstVolumeCover(manga: MangaDto): String? = fetchFirstVolumeCovers(listOf(manga.data!!))?.get(manga.data.id)

    /**
     * Attempt to get the first volume cover if the setting is enabled.
     * Uses the 'covers' endpoint.
     *
     * @see CoverArtListDto
     */
    private suspend fun fetchFirstVolumeCovers(mangaList: List<MangaDataDto>): Map<String, String>? {
        if (!preferences.tryUsingFirstVolumeCover || mangaList.isEmpty()) {
            return null
        }

        val safeMangaList = mangaList.filterNot { it.attributes?.originalLanguage.isNullOrEmpty() }
        val mangaMap = safeMangaList.associate { it.id to it.attributes!! }
        val locales = safeMangaList.mapNotNull { it.attributes!!.originalLanguage }.distinct()
        val limit = (mangaMap.size * locales.size).coerceAtMost(100)

        val apiUrl = "${Constants.API_URL}/cover".toHttpUrl().newBuilder()
            .addQueryParameter("order[volume]", "asc")
            .addQueryParameter("manga[]", mangaMap.keys)
            .addQueryParameter("locales[]", locales.toSet())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", "0")
            .build()

        val result = runCatching {
            client.get(apiUrl).parseAs<CoverArtListDto>().data
        }

        val covers = result.getOrNull() ?: return null

        return covers
            .groupBy { it.relationships.firstInstanceOrNull<MangaDataDto>()!!.id }
            .mapValues {
                it.value.find { c -> c.attributes?.locale == mangaMap[it.key]?.originalLanguage }
            }
            .filterValues { !it?.attributes?.fileName.isNullOrEmpty() }
            .mapValues { it.value!!.attributes!!.fileName!! }
    }

    // Chapter list section

    /**
     * Required because the chapter list API endpoint is paginated.
     */
    private fun paginatedChapterListUrl(mangaId: String, offset: Int): HttpUrl = helper
        .getChapterEndpoint(mangaId, offset, dexLang)
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("contentRating[]", Constants.allContentRatings)
        .addQueryParameter("excludedGroups[]", preferences.blockedGroups)
        .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
        .addQueryParameter("includeUnavailable", if (preferences.includeUnavailable) "1" else "0")
        .build()

    /**
     * Get the API endpoint URL for the first page of chapter list.
     *
     * @throws Exception if the url is the old format so people migrate
     */
    private suspend fun parseChapterList(manga: SManga): List<SChapter> {
        if (!helper.containsUuid(manga.url)) {
            throw Exception(helper.intl["migrate_warning"])
        }

        val response = client.get(paginatedChapterListUrl(helper.getUUIDFromUrl(manga.url), 0))

        if (response.code == 204) {
            return emptyList()
        }

        val chapterListResponse = response.parseAs<ChapterListDto>()

        val chapterListResults = chapterListResponse.data.toMutableList()

        val mangaId = response.request.url.toString()
            .substringBefore("/feed")
            .substringAfter("${Constants.API_MANGA_URL}/")

        var offset = chapterListResponse.offset
        var hasNextPage = chapterListResponse.hasNextPage

        // Max results that can be returned is 500 so need to make more API
        // calls if the chapter list response has a next page.
        while (hasNextPage) {
            offset += chapterListResponse.limit

            val newResponse = client.get(
                url = paginatedChapterListUrl(mangaId, offset),
                cacheControl = CacheControl.FORCE_NETWORK,
            )
            val newChapterList = newResponse.parseAs<ChapterListDto>()
            chapterListResults.addAll(newChapterList.data)

            hasNextPage = newChapterList.hasNextPage
        }

        return chapterListResults
            .filterNot { it.attributes!!.isInvalid }
            .map(helper::createChapter)
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!helper.containsUuid(chapter.url)) {
            throw Exception(helper.intl["migrate_warning"])
        }

        val chapterId = chapter.url.substringAfter("/chapter/")
        val atHomeRequestUrl = if (preferences.forceStandardHttps) {
            "${Constants.API_URL}/at-home/server/$chapterId?forcePort443=true"
        } else {
            "${Constants.API_URL}/at-home/server/$chapterId"
        }

        helper.mdRefreshToken(atHomeRequestUrl)
        val response = client.get(
            url = atHomeRequestUrl,
            cacheControl = CacheControl.FORCE_NETWORK,
        )
        val atHomeDto = response.parseAs<AtHomeDto>()
        val host = atHomeDto.baseUrl

        // Have to add the time, and url to the page because pages timeout within 30 minutes now.
        val now = Date().time

        val hash = atHomeDto.chapter.hash
        val pageSuffix = if (preferences.useDataSaver) {
            atHomeDto.chapter.dataSaver.map { "/data-saver/$hash/$it" }
        } else {
            atHomeDto.chapter.data.map { "/data/$hash/$it" }
        }

        return pageSuffix.mapIndexed { index, imgUrl ->
            val mdAtHomeMetadataUrl = "$host,$atHomeRequestUrl,$now"
            Page(index, mdAtHomeMetadataUrl, imgUrl)
        }
    }

    override fun imageRequest(page: Page): Request = runBlocking { helper.getValidImageUrlForPage(page, headers, client) }

    fun delegateImageRequest(page: Page): Request = imageRequest(page)

    @Suppress("UNCHECKED_CAST")
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val coverQualityPref = ListPreference(screen.context).apply {
            key = Constants.getCoverQualityPreferenceKey(dexLang)
            title = helper.intl["cover_quality"]
            entries = Constants.getCoverQualityPreferenceEntries(helper.intl)
            entryValues = Constants.getCoverQualityPreferenceEntryValues()
            setDefaultValue(Constants.getCoverQualityPreferenceDefaultValue())
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String

                preferences.edit()
                    .putString(Constants.getCoverQualityPreferenceKey(dexLang), entry)
                    .commit()
            }
        }

        val tryUsingFirstVolumeCoverPref = SwitchPreferenceCompat(screen.context).apply {
            key = Constants.getTryUsingFirstVolumeCoverPrefKey(dexLang)
            title = helper.intl["try_using_first_volume_cover"]
            summary = helper.intl["try_using_first_volume_cover_summary"]
            setDefaultValue(Constants.TRY_USING_FIRST_VOLUME_COVER_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(Constants.getTryUsingFirstVolumeCoverPrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val dataSaverPref = SwitchPreferenceCompat(screen.context).apply {
            key = Constants.getDataSaverPreferenceKey(dexLang)
            title = helper.intl["data_saver"]
            summary = helper.intl["data_saver_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(Constants.getDataSaverPreferenceKey(dexLang), checkValue)
                    .commit()
            }
        }

        val standardHttpsPortPref = SwitchPreferenceCompat(screen.context).apply {
            key = Constants.getStandardHttpsPreferenceKey(dexLang)
            title = helper.intl["standard_https_port"]
            summary = helper.intl["standard_https_port_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(Constants.getStandardHttpsPreferenceKey(dexLang), checkValue)
                    .commit()
            }
        }

        val contentRatingPref = MultiSelectListPreference(screen.context).apply {
            key = Constants.getContentRatingPrefKey(dexLang)
            title = helper.intl["standard_content_rating"]
            summary = helper.intl["standard_content_rating_summary"]
            entries = arrayOf(
                helper.intl["content_rating_safe"],
                helper.intl["content_rating_suggestive"],
                helper.intl["content_rating_erotica"],
                helper.intl["content_rating_pornographic"],
            )
            entryValues = arrayOf(
                Constants.CONTENT_RATING_PREF_VAL_SAFE,
                Constants.CONTENT_RATING_PREF_VAL_SUGGESTIVE,
                Constants.CONTENT_RATING_PREF_VAL_EROTICA,
                Constants.CONTENT_RATING_PREF_VAL_PORNOGRAPHIC,
            )
            setDefaultValue(Constants.contentRatingPrefDefaults)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Set<String>

                preferences.edit()
                    .putStringSet(Constants.getContentRatingPrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val originalLanguagePref = MultiSelectListPreference(screen.context).apply {
            key = Constants.getOriginalLanguagePrefKey(dexLang)
            title = helper.intl["filter_original_languages"]
            summary = helper.intl["filter_original_languages_summary"]
            entries = arrayOf(
                helper.intl.languageDisplayName(MDIntl.JAPANESE),
                helper.intl.languageDisplayName(MDIntl.CHINESE),
                helper.intl.languageDisplayName(MDIntl.KOREAN),
            )
            entryValues = arrayOf(
                Constants.ORIGINAL_LANGUAGE_PREF_VAL_JAPANESE,
                Constants.ORIGINAL_LANGUAGE_PREF_VAL_CHINESE,
                Constants.ORIGINAL_LANGUAGE_PREF_VAL_KOREAN,
            )
            setDefaultValue(Constants.originalLanguagePrefDefaults)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Set<String>

                preferences.edit()
                    .putStringSet(Constants.getOriginalLanguagePrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val blockedGroupsPref = EditTextPreference(screen.context).apply {
            key = Constants.getBlockedGroupsPrefKey(dexLang)
            title = helper.intl["block_group_by_uuid"]
            summary = helper.intl["block_group_by_uuid_summary"]

            setOnBindEditTextListener(helper::setupEditTextUuidValidator)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putString(Constants.getBlockedGroupsPrefKey(dexLang), newValue.toString())
                    .commit()
            }
        }

        val blockedUploaderPref = EditTextPreference(screen.context).apply {
            key = Constants.getBlockedUploaderPrefKey(dexLang)
            title = helper.intl["block_uploader_by_uuid"]
            summary = helper.intl["block_uploader_by_uuid_summary"]

            setOnBindEditTextListener(helper::setupEditTextUuidValidator)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putString(Constants.getBlockedUploaderPrefKey(dexLang), newValue.toString())
                    .commit()
            }
        }

        val altTitlesInDescPref = SwitchPreferenceCompat(screen.context).apply {
            key = Constants.getAltTitlesInDescPrefKey(dexLang)
            title = helper.intl["alternative_titles_in_description"]
            summary = helper.intl["alternative_titles_in_description_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(Constants.getAltTitlesInDescPrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val preferExtensionLangTitlePref = SwitchPreferenceCompat(screen.context).apply {
            key = Constants.getPreferExtensionLangTitlePrefKey(dexLang)
            title = helper.intl["prefer_title_in_extension_language"]
            summary = helper.intl["prefer_title_in_extension_language_summary"]
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(Constants.getPreferExtensionLangTitlePrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val finalChapterInDescPref = SwitchPreferenceCompat(screen.context).apply {
            key = Constants.getFinalChapterInDescPrefKey(dexLang)
            title = helper.intl["final_chapter_in_description"]
            summary = helper.intl["final_chapter_in_description_summary"]
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(Constants.getFinalChapterInDescPrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val includeUnavailablePref = SwitchPreferenceCompat(screen.context).apply {
            key = Constants.getIncludeUnavailablePrefKey(dexLang)
            title = helper.intl["include_unavailable"]
            summary = helper.intl["include_unavailable_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(Constants.getIncludeUnavailablePrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        screen.addPreference(coverQualityPref)
        screen.addPreference(tryUsingFirstVolumeCoverPref)
        screen.addPreference(dataSaverPref)
        screen.addPreference(standardHttpsPortPref)
        screen.addPreference(altTitlesInDescPref)
        screen.addPreference(preferExtensionLangTitlePref)
        screen.addPreference(finalChapterInDescPref)
        screen.addPreference(includeUnavailablePref)
        screen.addPreference(contentRatingPref)
        screen.addPreference(originalLanguagePref)
        screen.addPreference(blockedGroupsPref)
        screen.addPreference(blockedUploaderPref)
    }

    override fun getFilterList(data: JsonElement?): FilterList = helper.mdFilters.getMDFilterList(preferences, dexLang, helper.intl)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async {
            if (fetchDetails) getMangaByUrl(getMangaUrl(manga).toHttpUrl()) else manga
        }
        val chaptersDeferred = async {
            if (fetchChapters) parseChapterList(manga) else chapters
        }

        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private fun HttpUrl.Builder.addQueryParameter(name: String, value: Set<String>?) = apply {
        value?.forEach { addQueryParameter(name, it) }
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        helper.json.decodeFromString(body.string())
    }

    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? = firstOrNull { it is T } as? T?

    private val SharedPreferences.contentRating
        get() = getStringSet(
            Constants.getContentRatingPrefKey(dexLang),
            Constants.contentRatingPrefDefaults,
        )

    private val SharedPreferences.originalLanguages: Set<String>
        get() {
            val prefValues = getStringSet(
                Constants.getOriginalLanguagePrefKey(dexLang),
                Constants.originalLanguagePrefDefaults,
            )

            val originalLanguages = prefValues.orEmpty().toMutableSet()

            if (Constants.ORIGINAL_LANGUAGE_PREF_VAL_CHINESE in originalLanguages) {
                originalLanguages.add(Constants.ORIGINAL_LANGUAGE_PREF_VAL_CHINESE_HK)
            }

            return originalLanguages
        }

    private val SharedPreferences.coverQuality
        get() = getString(Constants.getCoverQualityPreferenceKey(dexLang), "")

    private val SharedPreferences.tryUsingFirstVolumeCover
        get() = getBoolean(
            Constants.getTryUsingFirstVolumeCoverPrefKey(dexLang),
            Constants.TRY_USING_FIRST_VOLUME_COVER_DEFAULT,
        )

    private val SharedPreferences.blockedGroups
        get() = getString(Constants.getBlockedGroupsPrefKey(dexLang), "")
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.sorted()
            .orEmpty()
            .toSet()

    private val SharedPreferences.blockedUploaders
        get() = getString(Constants.getBlockedUploaderPrefKey(dexLang), "")
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.sorted()
            .orEmpty()
            .toSet()

    private val SharedPreferences.forceStandardHttps
        get() = getBoolean(Constants.getStandardHttpsPreferenceKey(dexLang), false)

    private val SharedPreferences.useDataSaver
        get() = getBoolean(Constants.getDataSaverPreferenceKey(dexLang), false)

    private val SharedPreferences.altTitlesInDesc
        get() = getBoolean(Constants.getAltTitlesInDescPrefKey(dexLang), false)

    private val SharedPreferences.preferExtensionLangTitle
        get() = getBoolean(Constants.getPreferExtensionLangTitlePrefKey(dexLang), true)

    private val SharedPreferences.finalChapterInDesc
        get() = getBoolean(Constants.getFinalChapterInDescPrefKey(dexLang), true)

    private val SharedPreferences.includeUnavailable
        get() = getBoolean(Constants.getIncludeUnavailablePrefKey(dexLang), false)

    /**
     * Previous versions of the extension allowed invalid UUID values to be stored in the
     * preferences. This method clear invalid UUIDs in case the user have updated from
     * a previous version with that behaviour.
     */
    private fun SharedPreferences.sanitizeExistingUuidPrefs() {
        if (getBoolean(Constants.getHasSanitizedUuidsPrefKey(dexLang), false)) {
            return
        }

        val blockedGroups = getString(Constants.getBlockedGroupsPrefKey(dexLang), "")!!
            .split(",")
            .map(String::trim)
            .filter(helper::isUuid)
            .joinToString(", ")

        val blockedUploaders = getString(Constants.getBlockedUploaderPrefKey(dexLang), "")!!
            .split(",")
            .map(String::trim)
            .filter(helper::isUuid)
            .joinToString(", ")

        edit()
            .putString(Constants.getBlockedGroupsPrefKey(dexLang), blockedGroups)
            .putString(Constants.getBlockedUploaderPrefKey(dexLang), blockedUploaders)
            .putBoolean(Constants.getHasSanitizedUuidsPrefKey(dexLang), true)
            .apply()
    }
}
