package eu.kanade.tachiyomi.extension.all.mangadex

import android.app.Application
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
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

abstract class MangaDex(final override val lang: String, private val dexLang: String = lang) :
    ConfigurableSource, HttpSource() {

    override val name = MangaDexIntl.MANGADEX_NAME

    override val baseUrl = "https://mangadex.org"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
            .sanitizeExistingUuidPrefs()
    }

    private val helper = MangaDexHelper(lang)

    final override fun headersBuilder(): Headers.Builder {
        val extraHeader = "Android/${Build.VERSION.RELEASE} " +
            "Tachiyomi/${AppInfo.getVersionName()} " +
            "MangaDex/1.4.${BuildConfig.VERSION_CODE} " +
            "Keiyoushi"

        val builder = super.headersBuilder().apply {
            set("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
            set("Referer", "$baseUrl/")
            set("Origin", baseUrl)
            set("Extra", extraHeader)
        }

        return builder
    }

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .addInterceptor(MdAtHomeReportInterceptor(network.client, headers))
        .build()

    // Popular manga section

    override fun popularMangaRequest(page: Int): Request {
        val url = MDConstants.apiMangaUrl.toHttpUrl().newBuilder()
            .addQueryParameter("order[followedCount]", "desc")
            .addQueryParameter("availableTranslatedLanguage[]", dexLang)
            .addQueryParameter("limit", MDConstants.mangaLimit.toString())
            .addQueryParameter("offset", helper.getMangaListOffset(page))
            .addQueryParameter("includes[]", MDConstants.coverArt)
            .addQueryParameter("contentRating[]", preferences.contentRating)
            .addQueryParameter("originalLanguage[]", preferences.originalLanguages)
            .build()

        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    override fun popularMangaParse(response: Response): MangasPage {
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

    // Latest manga section

    override fun latestUpdatesRequest(page: Int): Request {
        val url = MDConstants.apiChapterUrl.toHttpUrl().newBuilder()
            .addQueryParameter("offset", helper.getLatestChapterOffset(page))
            .addQueryParameter("limit", MDConstants.latestChapterLimit.toString())
            .addQueryParameter("translatedLanguage[]", dexLang)
            .addQueryParameter("order[publishAt]", "desc")
            .addQueryParameter("includeFutureUpdates", "0")
            .addQueryParameter("originalLanguage[]", preferences.originalLanguages)
            .addQueryParameter("contentRating[]", preferences.contentRating)
            .addQueryParameter(
                "excludedGroups[]",
                MDConstants.defaultBlockedGroups + preferences.blockedGroups,
            )
            .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
            .addQueryParameter("includeFuturePublishAt", "0")
            .addQueryParameter("includeEmptyPages", "0")
            .build()

        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    /**
     * The API endpoint can't sort by date yet, so not implemented.
     */
    override fun latestUpdatesParse(response: Response): MangasPage {
        val chapterListDto = response.parseAs<ChapterListDto>()

        val mangaIds = chapterListDto.data
            .flatMap { it.relationships }
            .filterIsInstance<MangaDataDto>()
            .map { it.id }
            .distinct()
            .toSet()

        val mangaApiUrl = MDConstants.apiMangaUrl.toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", MDConstants.coverArt)
            .addQueryParameter("limit", mangaIds.size.toString())
            .addQueryParameter("contentRating[]", preferences.contentRating)
            .addQueryParameter("ids[]", mangaIds)
            .build()

        val mangaRequest = GET(mangaApiUrl, headers, CacheControl.FORCE_NETWORK)
        val mangaResponse = client.newCall(mangaRequest).execute()
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

    // Search manga section

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(MDConstants.prefixChSearch) ->
                getMangaIdFromChapterId(query.removePrefix(MDConstants.prefixChSearch))
                    .flatMap { mangaId ->
                        super.fetchSearchManga(
                            page = page,
                            query = MDConstants.prefixIdSearch + mangaId,
                            filters = filters,
                        )
                    }

            query.startsWith(MDConstants.prefixUsrSearch) ->
                client
                    .newCall(
                        request = searchMangaUploaderRequest(
                            page = page,
                            uploader = query.removePrefix(MDConstants.prefixUsrSearch),
                        ),
                    )
                    .asObservableSuccess()
                    .map { latestUpdatesParse(it) }

            query.startsWith(MDConstants.prefixListSearch) ->
                client
                    .newCall(
                        request = searchMangaListRequest(
                            list = query.removePrefix(MDConstants.prefixListSearch),
                        ),
                    )
                    .asObservableSuccess()
                    .map { searchMangaListParse(it, page, filters) }

            else -> super.fetchSearchManga(page, query.trim(), filters)
        }
    }

    private fun getMangaIdFromChapterId(id: String): Observable<String> {
        return client.newCall(GET("${MDConstants.apiChapterUrl}/$id", headers))
            .asObservable()
            .map { response ->
                if (response.isSuccessful.not()) {
                    throw Exception(helper.intl.format("unable_to_process_chapter_request", response.code))
                }

                response.parseAs<ChapterDto>().data!!.relationships
                    .firstInstanceOrNull<MangaDataDto>()!!.id
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(MDConstants.prefixIdSearch)) {
            val mangaId = query.removePrefix(MDConstants.prefixIdSearch)

            if (!helper.containsUuid(mangaId)) {
                throw Exception(helper.intl["invalid_manga_id"])
            }

            val url = MDConstants.apiMangaUrl.toHttpUrl().newBuilder()
                .addQueryParameter("ids[]", query.removePrefix(MDConstants.prefixIdSearch))
                .addQueryParameter("includes[]", MDConstants.coverArt)
                .addQueryParameter("contentRating[]", MDConstants.allContentRatings)
                .build()

            return GET(url, headers, CacheControl.FORCE_NETWORK)
        }

        val tempUrl = MDConstants.apiMangaUrl.toHttpUrl().newBuilder()
            .addQueryParameter("limit", MDConstants.mangaLimit.toString())
            .addQueryParameter("offset", helper.getMangaListOffset(page))
            .addQueryParameter("includes[]", MDConstants.coverArt)

        when {
            query.startsWith(MDConstants.prefixGrpSearch) -> {
                val groupId = query.removePrefix(MDConstants.prefixGrpSearch)

                if (!helper.containsUuid(groupId)) {
                    throw Exception(helper.intl["invalid_group_id"])
                }

                tempUrl.addQueryParameter("group", groupId)
            }

            query.startsWith(MDConstants.prefixAuthSearch) -> {
                val authorId = query.removePrefix(MDConstants.prefixAuthSearch)

                if (!helper.containsUuid(authorId)) {
                    throw Exception(helper.intl["invalid_author_id"])
                }

                tempUrl.addQueryParameter("authorOrArtist", authorId)
            }

            else -> {
                val actualQuery = query.replace(MDConstants.whitespaceRegex, " ")

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

        return GET(finalUrl, headers, CacheControl.FORCE_NETWORK)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private fun searchMangaListRequest(list: String): Request {
        return GET("${MDConstants.apiListUrl}/$list", headers, CacheControl.FORCE_NETWORK)
    }

    private fun searchMangaListParse(response: Response, page: Int, filters: FilterList): MangasPage {
        val listDto = response.parseAs<ListDto>()
        val listDtoFiltered = listDto.data!!.relationships.filterIsInstance<MangaDataDto>()
        val amount = listDtoFiltered.count()

        if (amount < 1) {
            throw Exception(helper.intl["no_series_in_list"])
        }

        val minIndex = (page - 1) * MDConstants.mangaLimit

        val tempUrl = MDConstants.apiMangaUrl.toHttpUrl().newBuilder()
            .addQueryParameter("limit", MDConstants.mangaLimit.toString())
            .addQueryParameter("offset", "0")
            .addQueryParameter("includes[]", MDConstants.coverArt)

        val ids = listDtoFiltered
            .filterIndexed { i, _ -> i >= minIndex && i < (minIndex + MDConstants.mangaLimit) }
            .map(MangaDataDto::id)
            .toSet()

        tempUrl.addQueryParameter("ids[]", ids)

        val finalUrl = helper.mdFilters.addFiltersToUrl(
            url = tempUrl,
            filters = filters.ifEmpty { getFilterList() },
            dexLang = dexLang,
        )

        val mangaRequest = GET(finalUrl, headers, CacheControl.FORCE_NETWORK)
        val mangaResponse = client.newCall(mangaRequest).execute()
        val mangaList = searchMangaListParse(mangaResponse)

        val hasNextPage = amount.toFloat() / MDConstants.mangaLimit - (page.toFloat() - 1) > 1 &&
            ids.size == MDConstants.mangaLimit

        return MangasPage(mangaList, hasNextPage)
    }

    private fun searchMangaListParse(response: Response): List<SManga> {
        // This check will be used as the source is doing additional requests to this
        // that are not parsed by the asObservableSuccess() method. It should throw the
        // HttpException from the app if it becomes available in a future version of extensions-lib.
        if (response.isSuccessful.not()) {
            throw Exception("HTTP error ${response.code}")
        }

        val mangaListDto = response.parseAs<MangaListDto>()
        val firstVolumeCovers = fetchFirstVolumeCovers(mangaListDto.data).orEmpty()

        val coverSuffix = preferences.coverQuality

        val mangaList = mangaListDto.data.map { mangaDataDto ->
            val fileName = firstVolumeCovers.getOrElse(mangaDataDto.id) {
                mangaDataDto.relationships
                    .firstInstanceOrNull<CoverArtDto>()
                    ?.attributes?.fileName
            }
            helper.createBasicManga(mangaDataDto, fileName, coverSuffix, dexLang, preferences.preferExtensionLangTitle)
        }

        return mangaList
    }

    private fun searchMangaUploaderRequest(page: Int, uploader: String): Request {
        val url = MDConstants.apiChapterUrl.toHttpUrl().newBuilder()
            .addQueryParameter("offset", helper.getLatestChapterOffset(page))
            .addQueryParameter("limit", MDConstants.latestChapterLimit.toString())
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
                MDConstants.defaultBlockedGroups + preferences.blockedGroups,
            )
            .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
            .build()

        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    // Manga Details section

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url.replace("/manga/", "/title/") + "/" + helper.titleToSlug(manga.title)
    }

    /**
     * Get the API endpoint URL for the entry details.
     *
     * @throws Exception if the url is the old format so people migrate
     */
    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!helper.containsUuid(manga.url.trim())) {
            throw Exception(helper.intl["migrate_warning"])
        }

        val url = (MDConstants.apiUrl + manga.url).toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", MDConstants.coverArt)
            .addQueryParameter("includes[]", MDConstants.author)
            .addQueryParameter("includes[]", MDConstants.artist)
            .build()

        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.parseAs<MangaDto>()

        return helper.createManga(
            manga.data!!,
            fetchSimpleChapterList(manga, dexLang),
            fetchFirstVolumeCover(manga),
            dexLang,
            preferences.coverQuality,
            preferences.altTitlesInDesc,
            preferences.preferExtensionLangTitle,
        )
    }

    /**
     * Get a quick-n-dirty list of the chapters to be used in determining the manga status.
     * Uses the 'aggregate' endpoint.
     *
     * @see MangaDexHelper.getPublicationStatus
     * @see AggregateDto
     */
    private fun fetchSimpleChapterList(manga: MangaDto, langCode: String): Map<String, AggregateVolume> {
        val url = "${MDConstants.apiMangaUrl}/${manga.data!!.id}/aggregate?translatedLanguage[]=$langCode"
        val response = client.newCall(GET(url, headers)).execute()

        return runCatching { response.parseAs<AggregateDto>() }
            .getOrNull()?.volumes.orEmpty()
    }

    /**
     * Attempt to get the first volume cover if the setting is enabled.
     * Uses the 'covers' endpoint.
     *
     * @see CoverArtListDto
     */
    private fun fetchFirstVolumeCover(manga: MangaDto): String? {
        return fetchFirstVolumeCovers(listOf(manga.data!!))?.get(manga.data.id)
    }

    /**
     * Attempt to get the first volume cover if the setting is enabled.
     * Uses the 'covers' endpoint.
     *
     * @see CoverArtListDto
     */
    private fun fetchFirstVolumeCovers(mangaList: List<MangaDataDto>): Map<String, String>? {
        if (!preferences.tryUsingFirstVolumeCover || mangaList.isEmpty()) {
            return null
        }

        val safeMangaList = mangaList.filterNot { it.attributes?.originalLanguage.isNullOrEmpty() }
        val mangaMap = safeMangaList.associate { it.id to it.attributes!! }
        val locales = safeMangaList.mapNotNull { it.attributes!!.originalLanguage }.distinct()
        val limit = (mangaMap.size * locales.size).coerceAtMost(100)

        val apiUrl = "${MDConstants.apiUrl}/cover".toHttpUrl().newBuilder()
            .addQueryParameter("order[volume]", "asc")
            .addQueryParameter("manga[]", mangaMap.keys)
            .addQueryParameter("locales[]", locales.toSet())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", "0")
            .build()

        val result = runCatching {
            client.newCall(GET(apiUrl, headers)).execute().parseAs<CoverArtListDto>().data
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
     * Get the API endpoint URL for the first page of chapter list.
     *
     * @throws Exception if the url is the old format so people migrate
     */
    override fun chapterListRequest(manga: SManga): Request {
        if (!helper.containsUuid(manga.url)) {
            throw Exception(helper.intl["migrate_warning"])
        }

        return paginatedChapterListRequest(helper.getUUIDFromUrl(manga.url), 0)
    }

    /**
     * Required because the chapter list API endpoint is paginated.
     */
    private fun paginatedChapterListRequest(mangaId: String, offset: Int): Request {
        val url = helper.getChapterEndpoint(mangaId, offset, dexLang).toHttpUrl().newBuilder()
            .addQueryParameter("contentRating[]", MDConstants.allContentRatings)
            .addQueryParameter("excludedGroups[]", preferences.blockedGroups)
            .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
            .build()

        return GET(url, headers, CacheControl.FORCE_NETWORK)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.code == 204) {
            return emptyList()
        }

        val chapterListResponse = response.parseAs<ChapterListDto>()

        val chapterListResults = chapterListResponse.data.toMutableList()

        val mangaId = response.request.url.toString()
            .substringBefore("/feed")
            .substringAfter("${MDConstants.apiMangaUrl}/")

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

        return chapterListResults
            .filterNot { it.attributes!!.isInvalid }
            .map(helper::createChapter)
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        if (!helper.containsUuid(chapter.url)) {
            throw Exception(helper.intl["migrate_warning"])
        }

        val chapterId = chapter.url.substringAfter("/chapter/")
        val atHomeRequestUrl = if (preferences.forceStandardHttps) {
            "${MDConstants.apiUrl}/at-home/server/$chapterId?forcePort443=true"
        } else {
            "${MDConstants.apiUrl}/at-home/server/$chapterId"
        }

        return helper.mdAtHomeRequest(atHomeRequestUrl, headers, CacheControl.FORCE_NETWORK)
    }

    override fun pageListParse(response: Response): List<Page> {
        val atHomeRequestUrl = response.request.url
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

    override fun imageRequest(page: Page): Request {
        return helper.getValidImageUrlForPage(page, headers, client)
    }

    override fun imageUrlParse(response: Response): String = ""

    @Suppress("UNCHECKED_CAST")
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val coverQualityPref = ListPreference(screen.context).apply {
            key = MDConstants.getCoverQualityPreferenceKey(dexLang)
            title = helper.intl["cover_quality"]
            entries = MDConstants.getCoverQualityPreferenceEntries(helper.intl)
            entryValues = MDConstants.getCoverQualityPreferenceEntryValues()
            setDefaultValue(MDConstants.getCoverQualityPreferenceDefaultValue())
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String

                preferences.edit()
                    .putString(MDConstants.getCoverQualityPreferenceKey(dexLang), entry)
                    .commit()
            }
        }

        val tryUsingFirstVolumeCoverPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getTryUsingFirstVolumeCoverPrefKey(dexLang)
            title = helper.intl["try_using_first_volume_cover"]
            summary = helper.intl["try_using_first_volume_cover_summary"]
            setDefaultValue(MDConstants.tryUsingFirstVolumeCoverDefault)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(MDConstants.getTryUsingFirstVolumeCoverPrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val dataSaverPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getDataSaverPreferenceKey(dexLang)
            title = helper.intl["data_saver"]
            summary = helper.intl["data_saver_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(MDConstants.getDataSaverPreferenceKey(dexLang), checkValue)
                    .commit()
            }
        }

        val standardHttpsPortPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getStandardHttpsPreferenceKey(dexLang)
            title = helper.intl["standard_https_port"]
            summary = helper.intl["standard_https_port_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(MDConstants.getStandardHttpsPreferenceKey(dexLang), checkValue)
                    .commit()
            }
        }

        val contentRatingPref = MultiSelectListPreference(screen.context).apply {
            key = MDConstants.getContentRatingPrefKey(dexLang)
            title = helper.intl["standard_content_rating"]
            summary = helper.intl["standard_content_rating_summary"]
            entries = arrayOf(
                helper.intl["content_rating_safe"],
                helper.intl["content_rating_suggestive"],
                helper.intl["content_rating_erotica"],
                helper.intl["content_rating_pornographic"],
            )
            entryValues = arrayOf(
                MDConstants.contentRatingPrefValSafe,
                MDConstants.contentRatingPrefValSuggestive,
                MDConstants.contentRatingPrefValErotica,
                MDConstants.contentRatingPrefValPornographic,
            )
            setDefaultValue(MDConstants.contentRatingPrefDefaults)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Set<String>

                preferences.edit()
                    .putStringSet(MDConstants.getContentRatingPrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val originalLanguagePref = MultiSelectListPreference(screen.context).apply {
            key = MDConstants.getOriginalLanguagePrefKey(dexLang)
            title = helper.intl["filter_original_languages"]
            summary = helper.intl["filter_original_languages_summary"]
            entries = arrayOf(
                helper.intl.languageDisplayName(MangaDexIntl.JAPANESE),
                helper.intl.languageDisplayName(MangaDexIntl.CHINESE),
                helper.intl.languageDisplayName(MangaDexIntl.KOREAN),
            )
            entryValues = arrayOf(
                MDConstants.originalLanguagePrefValJapanese,
                MDConstants.originalLanguagePrefValChinese,
                MDConstants.originalLanguagePrefValKorean,
            )
            setDefaultValue(MDConstants.originalLanguagePrefDefaults)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Set<String>

                preferences.edit()
                    .putStringSet(MDConstants.getOriginalLanguagePrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val blockedGroupsPref = EditTextPreference(screen.context).apply {
            key = MDConstants.getBlockedGroupsPrefKey(dexLang)
            title = helper.intl["block_group_by_uuid"]
            summary = helper.intl["block_group_by_uuid_summary"]

            setOnBindEditTextListener(helper::setupEditTextUuidValidator)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putString(MDConstants.getBlockedGroupsPrefKey(dexLang), newValue.toString())
                    .commit()
            }
        }

        val blockedUploaderPref = EditTextPreference(screen.context).apply {
            key = MDConstants.getBlockedUploaderPrefKey(dexLang)
            title = helper.intl["block_uploader_by_uuid"]
            summary = helper.intl["block_uploader_by_uuid_summary"]

            setOnBindEditTextListener(helper::setupEditTextUuidValidator)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putString(MDConstants.getBlockedUploaderPrefKey(dexLang), newValue.toString())
                    .commit()
            }
        }

        val altTitlesInDescPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getAltTitlesInDescPrefKey(dexLang)
            title = helper.intl["alternative_titles_in_description"]
            summary = helper.intl["alternative_titles_in_description_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(MDConstants.getAltTitlesInDescPrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        val preferExtensionLangTitlePref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getPreferExtensionLangTitlePrefKey(dexLang)
            title = helper.intl["prefer_title_in_extension_language"]
            summary = helper.intl["prefer_title_in_extension_language_summary"]
            setDefaultValue(true)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean

                preferences.edit()
                    .putBoolean(MDConstants.getPreferExtensionLangTitlePrefKey(dexLang), checkValue)
                    .commit()
            }
        }

        screen.addPreference(coverQualityPref)
        screen.addPreference(tryUsingFirstVolumeCoverPref)
        screen.addPreference(dataSaverPref)
        screen.addPreference(standardHttpsPortPref)
        screen.addPreference(altTitlesInDescPref)
        screen.addPreference(preferExtensionLangTitlePref)
        screen.addPreference(contentRatingPref)
        screen.addPreference(originalLanguagePref)
        screen.addPreference(blockedGroupsPref)
        screen.addPreference(blockedUploaderPref)
    }

    override fun getFilterList(): FilterList =
        helper.mdFilters.getMDFilterList(preferences, dexLang, helper.intl)

    private fun HttpUrl.Builder.addQueryParameter(name: String, value: Set<String>?) = apply {
        value?.forEach { addQueryParameter(name, it) }
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        helper.json.decodeFromString(body.string())
    }

    private inline fun <reified T> List<*>.firstInstanceOrNull(): T? =
        firstOrNull { it is T } as? T?

    private val SharedPreferences.contentRating
        get() = getStringSet(
            MDConstants.getContentRatingPrefKey(dexLang),
            MDConstants.contentRatingPrefDefaults,
        )

    private val SharedPreferences.originalLanguages: Set<String>
        get() {
            val prefValues = getStringSet(
                MDConstants.getOriginalLanguagePrefKey(dexLang),
                MDConstants.originalLanguagePrefDefaults,
            )

            val originalLanguages = prefValues.orEmpty().toMutableSet()

            if (MDConstants.originalLanguagePrefValChinese in originalLanguages) {
                originalLanguages.add(MDConstants.originalLanguagePrefValChineseHk)
            }

            return originalLanguages
        }

    private val SharedPreferences.coverQuality
        get() = getString(MDConstants.getCoverQualityPreferenceKey(dexLang), "")

    private val SharedPreferences.tryUsingFirstVolumeCover
        get() = getBoolean(
            MDConstants.getTryUsingFirstVolumeCoverPrefKey(dexLang),
            MDConstants.tryUsingFirstVolumeCoverDefault,
        )

    private val SharedPreferences.blockedGroups
        get() = getString(MDConstants.getBlockedGroupsPrefKey(dexLang), "")
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.sorted()
            .orEmpty()
            .toSet()

    private val SharedPreferences.blockedUploaders
        get() = getString(MDConstants.getBlockedUploaderPrefKey(dexLang), "")
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.sorted()
            .orEmpty()
            .toSet()

    private val SharedPreferences.forceStandardHttps
        get() = getBoolean(MDConstants.getStandardHttpsPreferenceKey(dexLang), false)

    private val SharedPreferences.useDataSaver
        get() = getBoolean(MDConstants.getDataSaverPreferenceKey(dexLang), false)

    private val SharedPreferences.altTitlesInDesc
        get() = getBoolean(MDConstants.getAltTitlesInDescPrefKey(dexLang), false)

    private val SharedPreferences.preferExtensionLangTitle
        get() = getBoolean(MDConstants.getPreferExtensionLangTitlePrefKey(dexLang), true)

    /**
     * Previous versions of the extension allowed invalid UUID values to be stored in the
     * preferences. This method clear invalid UUIDs in case the user have updated from
     * a previous version with that behaviour.
     */
    private fun SharedPreferences.sanitizeExistingUuidPrefs(): SharedPreferences {
        if (getBoolean(MDConstants.getHasSanitizedUuidsPrefKey(dexLang), false)) {
            return this
        }

        val blockedGroups = getString(MDConstants.getBlockedGroupsPrefKey(dexLang), "")!!
            .split(",")
            .map(String::trim)
            .filter(helper::isUuid)
            .joinToString(", ")

        val blockedUploaders = getString(MDConstants.getBlockedUploaderPrefKey(dexLang), "")!!
            .split(",")
            .map(String::trim)
            .filter(helper::isUuid)
            .joinToString(", ")

        edit()
            .putString(MDConstants.getBlockedGroupsPrefKey(dexLang), blockedGroups)
            .putString(MDConstants.getBlockedUploaderPrefKey(dexLang), blockedUploaders)
            .putBoolean(MDConstants.getHasSanitizedUuidsPrefKey(dexLang), true)
            .apply()

        return this
    }
}
