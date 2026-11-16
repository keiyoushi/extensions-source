package eu.kanade.tachiyomi.extension.all.mangadex

import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AggregateDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AtHomeDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.AuthorDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CoverDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.CustomListDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.DataDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.mangadex.dto.PagedDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

@Source
class MangaDex(
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
    override val id: Long,
) : KeiSource(),
    ConfigurableSource {

    private val dexLang = when (lang) {
        "zh-Hans" -> "zh"
        "zh-Hant" -> "zh-hk"
        "fil" -> "tl"
        "pt-BR" -> "pt-br"
        "es-419" -> "es-la"
        else -> lang
    }

    private val translatedLanguages = if (dexLang == MDConstants.ALL_LANGUAGES) emptySet() else setOf(dexLang)

    private val helper = MangaDexHelper(lang)

    private val preferences by getPreferencesLazy { sanitizeExistingUuidPrefs() }

    @Suppress("DEPRECATION")
    private fun isDelegate() = try {
        val supportsDelegate = applicationContext.packageName.let {
            it.startsWith("app.komikku") || it.startsWith("eu.kanade.tachiyomi.sy")
        }
        PreferenceManager.getDefaultSharedPreferences(applicationContext)!!.getBoolean("eh_delegate_sources", supportsDelegate)
    } catch (_: Throwable) {
        false
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        val extraHeader = "Android/${Build.VERSION.RELEASE} " +
            "Tachiyomi/${AppInfo.getVersionName()} " +
            "MangaDex/${BuildConfig.VERSION_NAME} " +
            "Keiyoushi"

        set("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
        set("Extra", extraHeader)
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = mangaListUrl(page)
            .addQueryParameter("order[followedCount]", "desc")
            .addQueryParameter("hasAvailableChapters", "true")
            .addQueryParameter("availableTranslatedLanguage[]", translatedLanguages)
            .addQueryParameter("contentRating[]", preferences.contentRating)
            .addQueryParameter("originalLanguage[]", preferences.originalLanguages)
            .addQueryParameter(
                "createdAtSince",
                LocalDate.now()
                    .minusDays(30)
                    .atStartOfDay()
                    .atZone(ZoneOffset.UTC)
                    .format(MDConstants.dateFormatterNoOffset),
            )
            .build()

        return getMangasPage(url)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = getChapterMangasPage(latestUpdatesUrl(page))

    /**
     * Used by Komikku
     */
    fun latestUpdatesRequest(page: Int): Request = GET(latestUpdatesUrl(page), headers, CacheControl.FORCE_NETWORK).newBuilder().fetch(Fetch.XHR).build()

    /**
     * Used by Komikku
     */
    fun latestUpdatesParse(response: Response): MangasPage {
        val chapters = response.parseAs<PagedDto<ChapterDto>>()
        val mangaIds = chapters.data.map { it.mangaId }.distinct()
        if (mangaIds.isEmpty()) return MangasPage(emptyList(), false)

        val mangaList = blockingGet(mangaByIdsUrl(mangaIds)).parseAs<PagedDto<MangaDto>>().data
        val covers = coversUrl(mangaList)
            ?.let { firstVolumeCovers(mangaList, blockingGet(it).parseAs<PagedDto<CoverDto>>().data) }
            .orEmpty()

        return MangasPage(mangaList.orderedBy(mangaIds).toSMangaList(covers), chapters.hasNextPage)
    }

    private fun blockingGet(url: HttpUrl): Response = client.newCall(GET(url, headers, CacheControl.FORCE_NETWORK).newBuilder().fetch(Fetch.XHR).build()).execute()

    private fun latestUpdatesUrl(page: Int, uploader: String? = null): HttpUrl = MDConstants.API_CHAPTER_URL.toHttpUrl().newBuilder()
        .addQueryParameter("offset", (MDConstants.LATEST_CHAPTER_LIMIT * (page - 1)).toString())
        .addQueryParameter("limit", MDConstants.LATEST_CHAPTER_LIMIT.toString())
        .addQueryParameter("translatedLanguage[]", translatedLanguages)
        .addQueryParameter("order[publishAt]", "desc")
        .addQueryParameter("includeFutureUpdates", if (isDelegate()) "1" else "0")
        .addQueryParameter("includeFuturePublishAt", "0")
        .addQueryParameter("includeEmptyPages", "0")
        .apply { uploader?.let { addQueryParameter("uploader", it) } }
        .addQueryParameter("originalLanguage[]", preferences.originalLanguages)
        .addQueryParameter("contentRating[]", preferences.contentRating)
        .addQueryParameter("excludedGroups[]", MDConstants.defaultBlockedGroups + preferences.blockedGroups)
        .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
        .build()

    /** Manga of the chapters at [url], in the order of the chapters. */
    private suspend fun getChapterMangasPage(url: HttpUrl): MangasPage {
        val chapters = client.get(url, Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<PagedDto<ChapterDto>>()
        val mangaIds = chapters.data.map { it.mangaId }.distinct()
        if (mangaIds.isEmpty()) return MangasPage(emptyList(), false)

        val mangaList = client.get(mangaByIdsUrl(mangaIds), Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<PagedDto<MangaDto>>().data

        return MangasPage(mangaList.orderedBy(mangaIds).toSMangaList(fetchFirstVolumeCovers(mangaList)), chapters.hasNextPage)
    }

    private fun mangaByIdsUrl(mangaIds: List<String>): HttpUrl = MDConstants.API_MANGA_URL.toHttpUrl().newBuilder()
        .addQueryParameter("includes[]", MDConstants.COVER_ART)
        .addQueryParameter("limit", mangaIds.size.toString())
        .addQueryParameter("contentRating[]", preferences.contentRating)
        .addQueryParameter("ids[]", mangaIds)
        .build()

    private fun List<MangaDto>.orderedBy(mangaIds: List<String>): List<MangaDto> {
        val byId = associateBy { it.id }
        return mangaIds.mapNotNull(byId::get)
    }

    // Search

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.endsWith("mangadex.org")) {
            val searchPrefix = url.pathSegments.firstOrNull()?.let { MDConstants.pathToSearchPrefix[it] }
            val uuid = MDConstants.uuidRegex.find(url.toString())?.value
            if (searchPrefix != null && uuid != null) {
                return getSearchMangaList(page, searchPrefix + uuid, FilterList())
            }
        }
        return getSearchMangaList(page, url.toString(), FilterList())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = when {
        query.startsWith(MDConstants.PREFIX_ID_SEARCH) ->
            getMangaByIdPage(query.removePrefix(MDConstants.PREFIX_ID_SEARCH))

        query.startsWith(MDConstants.PREFIX_CH_SEARCH) ->
            getMangaByIdPage(getMangaIdFromChapterId(query.removePrefix(MDConstants.PREFIX_CH_SEARCH)))

        query.startsWith(MDConstants.PREFIX_USER_SEARCH) ->
            getChapterMangasPage(latestUpdatesUrl(page, uploader = query.removePrefix(MDConstants.PREFIX_USER_SEARCH)))

        query.startsWith(MDConstants.PREFIX_LIST_SEARCH) ->
            getListPage(query.removePrefix(MDConstants.PREFIX_LIST_SEARCH), page, filters)

        else -> {
            val url = mangaListUrl(page)

            when {
                query.startsWith(MDConstants.PREFIX_GRP_SEARCH) ->
                    url.addQueryParameter("group", query.removePrefix(MDConstants.PREFIX_GRP_SEARCH).requireUuid("invalid_group_id"))

                query.startsWith(MDConstants.PREFIX_AUTHOR_SEARCH) ->
                    url.addQueryParameter("authorOrArtist", query.removePrefix(MDConstants.PREFIX_AUTHOR_SEARCH).requireUuid("invalid_author_id"))

                else -> query.replace(MDConstants.whitespaceRegex, " ").trim()
                    .takeIf(String::isNotEmpty)
                    ?.let { url.addQueryParameter("title", it) }
            }

            if (!query.startsWith(MDConstants.PREFIX_AUTHOR_SEARCH)) {
                addAuthorFilter(url, filters)
            }

            getMangasPage(helper.mdFilters.addFiltersToUrl(url, filters, dexLang), CacheControl.FORCE_NETWORK)
        }
    }

    private suspend fun getMangaByIdPage(mangaId: String): MangasPage {
        val url = MDConstants.API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("ids[]", mangaId.requireUuid("invalid_manga_id"))
            .addQueryParameter("includes[]", MDConstants.COVER_ART)
            .addQueryParameter("contentRating[]", MDConstants.allContentRatings)
            .build()

        return getMangasPage(url, CacheControl.FORCE_NETWORK)
    }

    private suspend fun getMangaIdFromChapterId(id: String): String {
        val response = client.get("${MDConstants.API_CHAPTER_URL}/$id", Fetch.XHR, ensureSuccess = false)

        if (!response.isSuccessful) {
            response.close()
            throw Exception(helper.intl.format("unable_to_process_chapter_request", response.code))
        }

        return response.parseAs<DataDto<ChapterDto>>().data.mangaId
    }

    private suspend fun getListPage(listId: String, page: Int, filters: FilterList): MangasPage {
        val mangaIds = client.get("${MDConstants.API_LIST_URL}/$listId", Fetch.XHR, CacheControl.FORCE_NETWORK)
            .parseAs<DataDto<CustomListDto>>()
            .data.relationships
            .filter { it.type == MDConstants.MANGA }
            .map { it.id }

        if (mangaIds.isEmpty()) {
            throw Exception(helper.intl["no_series_in_list"])
        }

        val offset = (page - 1) * MDConstants.MANGA_LIMIT
        val url = MDConstants.API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("limit", MDConstants.MANGA_LIMIT.toString())
            .addQueryParameter("includes[]", MDConstants.COVER_ART)
            .addQueryParameter("ids[]", mangaIds.drop(offset).take(MDConstants.MANGA_LIMIT))
        addAuthorFilter(url, filters)

        val mangaList = client.get(helper.mdFilters.addFiltersToUrl(url, filters, dexLang), Fetch.XHR, CacheControl.FORCE_NETWORK)
            .parseAs<PagedDto<MangaDto>>().data

        return MangasPage(mangaList.toSMangaList(fetchFirstVolumeCovers(mangaList)), offset + MDConstants.MANGA_LIMIT < mangaIds.size)
    }

    private suspend fun addAuthorFilter(url: HttpUrl.Builder, filters: FilterList) {
        val name = filters.firstInstanceOrNull<MangaDexFilters.AuthorFilter>()?.state?.trim()
        if (name.isNullOrEmpty()) return

        val authorUrl = MDConstants.API_AUTHOR_URL.toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("limit", "1")
            .build()
        val authorId = client.get(authorUrl, Fetch.XHR).parseAs<PagedDto<AuthorDto>>().data.firstOrNull()?.id
            ?: throw Exception(helper.intl.format("author_not_found", name))

        url.addQueryParameter("authorOrArtist", authorId)
    }

    private fun String.requireUuid(errorKey: String): String = trim().takeIf(MDConstants.uuidRegex::matches)
        ?: throw Exception(helper.intl[errorKey])

    // Manga lists

    private fun mangaListUrl(page: Int): HttpUrl.Builder = MDConstants.API_MANGA_URL.toHttpUrl().newBuilder()
        .addQueryParameter("limit", MDConstants.MANGA_LIMIT.toString())
        .addQueryParameter("offset", (MDConstants.MANGA_LIMIT * (page - 1)).toString())
        .addQueryParameter("includes[]", MDConstants.COVER_ART)

    private suspend fun getMangasPage(url: HttpUrl, cacheControl: CacheControl? = null): MangasPage {
        val response = if (cacheControl != null) client.get(url, Fetch.XHR, cacheControl) else client.get(url, Fetch.XHR)
        if (response.code == 204) {
            response.close()
            return MangasPage(emptyList(), false)
        }

        val mangaList = response.parseAs<PagedDto<MangaDto>>()

        return MangasPage(mangaList.data.toSMangaList(fetchFirstVolumeCovers(mangaList.data)), mangaList.hasNextPage)
    }

    private fun List<MangaDto>.toSMangaList(firstVolumeCovers: Map<String, String>): List<SManga> {
        val coverSuffix = preferences.coverQuality
        val preferExtensionLangTitle = preferences.preferExtensionLangTitle

        return map { manga ->
            helper.createBasicManga(manga, firstVolumeCovers[manga.id] ?: manga.coverFileName, coverSuffix, dexLang, preferExtensionLangTitle)
        }
    }

    // Covers

    private suspend fun fetchFirstVolumeCovers(mangaList: List<MangaDto>): Map<String, String> {
        val url = coversUrl(mangaList) ?: return emptyMap()
        return firstVolumeCovers(mangaList, client.get(url, Fetch.XHR).parseAs<PagedDto<CoverDto>>().data)
    }

    private fun coversUrl(mangaList: List<MangaDto>): HttpUrl? {
        if (!preferences.tryUsingFirstVolumeCover || mangaList.isEmpty()) return null

        val locales = mangaList.map { it.attributes.originalLanguage }.toSet()

        return "${MDConstants.API_URL}/cover".toHttpUrl().newBuilder()
            .addQueryParameter("order[volume]", "asc")
            .addQueryParameter("manga[]", mangaList.map { it.id })
            .addQueryParameter("locales[]", locales)
            .addQueryParameter("limit", (mangaList.size * locales.size).coerceAtMost(100).toString())
            .build()
    }

    /** The first cover of each manga in its original language; [covers] are sorted by volume. */
    private fun firstVolumeCovers(mangaList: List<MangaDto>, covers: List<CoverDto>): Map<String, String> {
        val originalLanguages = mangaList.associate { it.id to it.attributes.originalLanguage }

        return covers
            .filter { it.attributes.locale == originalLanguages[it.mangaId] }
            .distinctBy { it.mangaId }
            .associate { it.mangaId to it.attributes.fileName }
    }

    // Details

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.replace("/manga/", "/title/") + "/" + helper.titleToSlug(manga.title)

    private suspend fun getMangaDetails(manga: SManga): SManga = coroutineScope {
        val mangaId = manga.url.uuid()

        val aggregate = async {
            val url = "${MDConstants.API_MANGA_URL}/$mangaId/aggregate".toHttpUrl().newBuilder()
                .addQueryParameter("translatedLanguage[]", translatedLanguages)
                .build()
            client.get(url, Fetch.XHR).parseAs<AggregateDto>().volumes
        }

        val url = "${MDConstants.API_MANGA_URL}/$mangaId".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", MDConstants.COVER_ART)
            .addQueryParameter("includes[]", MDConstants.AUTHOR)
            .addQueryParameter("includes[]", MDConstants.ARTIST)
            .build()
        val mangaDto = client.get(url, Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<DataDto<MangaDto>>().data
        val firstVolumeCover = fetchFirstVolumeCovers(listOf(mangaDto))[mangaDto.id]

        helper.createManga(
            mangaDto,
            aggregate.await(),
            firstVolumeCover,
            dexLang,
            preferences.coverQuality,
            preferences.altTitlesInDesc,
            preferences.preferExtensionLangTitle,
            preferences.finalChapterInDesc,
        )
    }

    // Chapters

    private suspend fun getChapterList(manga: SManga): List<SChapter> = coroutineScope {
        val mangaId = manga.url.uuid()

        val firstPage = client.get(chapterFeedUrl(mangaId, 0), Fetch.XHR, CacheControl.FORCE_NETWORK)
            .parseAs<PagedDto<ChapterDto>>()

        val otherPages = (firstPage.limit until firstPage.total step firstPage.limit).map { offset ->
            async {
                client.get(chapterFeedUrl(mangaId, offset), Fetch.XHR, CacheControl.FORCE_NETWORK)
                    .parseAs<PagedDto<ChapterDto>>().data
            }
        }

        (firstPage.data + otherPages.awaitAll().flatten())
            .filterNot { it.attributes.isInvalid }
            .map(helper::createChapter)
    }

    private fun chapterFeedUrl(mangaId: String, offset: Int): HttpUrl = "${MDConstants.API_MANGA_URL}/$mangaId/feed".toHttpUrl().newBuilder()
        .addQueryParameter("includes[]", MDConstants.SCANLATION_GROUP)
        .addQueryParameter("includes[]", MDConstants.USER)
        .addQueryParameter("limit", MDConstants.CHAPTER_FEED_LIMIT.toString())
        .addQueryParameter("offset", offset.toString())
        .addQueryParameter("order[volume]", "desc")
        .addQueryParameter("order[chapter]", "desc")
        .addQueryParameter("includeFuturePublishAt", "0")
        .addQueryParameter("translatedLanguage[]", translatedLanguages)
        .addQueryParameter("contentRating[]", MDConstants.allContentRatings)
        .addQueryParameter("excludedGroups[]", preferences.blockedGroups)
        .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
        .addQueryParameter("includeUnavailable", if (preferences.includeUnavailable) "1" else "0")
        .build()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }

        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    /** Throws for urls from MangaDex v3, which used numeric ids, so people migrate. */
    private fun String.uuid(): String = MDConstants.uuidRegex.find(this)?.value
        ?: throw Exception(helper.intl["migrate_warning"])

    // Pages

    /** MD@Home base urls by the at-home url they came from, reused until they expire. */
    private val atHomeHosts = ConcurrentHashMap<String, AtHomeHost>()

    private class AtHomeHost(val url: String, val fetchedAt: Long)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // the API has no pages for these, so don't hit it for every chapter of a download queue
        if (chapter.memo[MDConstants.MEMO_UNAVAILABLE]?.booleanOrNull == true) {
            throw Exception(helper.intl["chapter_unavailable"])
        }

        val atHomeUrl = "${MDConstants.API_URL}/at-home/server/${chapter.url.uuid()}" +
            if (preferences.forceStandardHttps) "?forcePort443=true" else ""

        val atHome = client.get(atHomeUrl, Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<AtHomeDto>()
        val now = System.currentTimeMillis()
        atHomeHosts[atHomeUrl] = AtHomeHost(atHome.baseUrl, now)

        val hash = atHome.chapter.hash
        val paths = if (preferences.useDataSaver) {
            atHome.chapter.dataSaver.map { "/data-saver/$hash/$it" }
        } else {
            atHome.chapter.data.map { "/data/$hash/$it" }
        }

        // TachiyomiSY and Komikku build MD@Home pages in this format too, and load them through getImageRequest
        return paths.mapIndexed { index, path -> Page(index, "${atHome.baseUrl},$atHomeUrl,$now", path) }
    }

    override fun getImageRequest(page: Page): Request {
        val (host, atHomeUrl, fetchedAt) = page.url.split(",")
        val validHost = if (System.currentTimeMillis() - fetchedAt.toLong() < MDConstants.mdAtHomeTokenLifespan) host else atHomeHost(atHomeUrl)
        return GET(validHost + page.imageUrl, headers)
    }

    /** MD@Home urls expire, so pages loaded later need a fresh one. */
    private fun atHomeHost(atHomeUrl: String): String {
        val now = System.currentTimeMillis()
        atHomeHosts[atHomeUrl]
            ?.takeIf { now - it.fetchedAt < MDConstants.mdAtHomeTokenLifespan }
            ?.let { return it.url }

        val host = blockingGet(atHomeUrl.toHttpUrl()).parseAs<AtHomeDto>().baseUrl
        atHomeHosts[atHomeUrl] = AtHomeHost(host, now)
        return host
    }

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val coverQualityPref = ListPreference(screen.context).apply {
            key = MDConstants.getCoverQualityPreferenceKey(dexLang)
            title = helper.intl["cover_quality"]
            entries = MDConstants.getCoverQualityPreferenceEntries(helper.intl)
            entryValues = MDConstants.getCoverQualityPreferenceEntryValues()
            setDefaultValue(MDConstants.getCoverQualityPreferenceDefaultValue())
            summary = "%s"
        }

        val tryUsingFirstVolumeCoverPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getTryUsingFirstVolumeCoverPrefKey(dexLang)
            title = helper.intl["try_using_first_volume_cover"]
            summary = helper.intl["try_using_first_volume_cover_summary"]
            setDefaultValue(MDConstants.TRY_USING_FIRST_VOLUME_COVER_DEFAULT)
        }

        val dataSaverPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getDataSaverPreferenceKey(dexLang)
            title = helper.intl["data_saver"]
            summary = helper.intl["data_saver_summary"]
            setDefaultValue(false)
        }

        val standardHttpsPortPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getStandardHttpsPreferenceKey(dexLang)
            title = helper.intl["standard_https_port"]
            summary = helper.intl["standard_https_port_summary"]
            setDefaultValue(false)
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
                MDConstants.CONTENT_RATING_PREF_VAL_SAFE,
                MDConstants.CONTENT_RATING_PREF_VAL_SUGGESTIVE,
                MDConstants.CONTENT_RATING_PREF_VAL_EROTICA,
                MDConstants.CONTENT_RATING_PREF_VAL_PORNOGRAPHIC,
            )
            setDefaultValue(MDConstants.contentRatingPrefDefaults)
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
                MDConstants.ORIGINAL_LANGUAGE_PREF_VAL_JAPANESE,
                MDConstants.ORIGINAL_LANGUAGE_PREF_VAL_CHINESE,
                MDConstants.ORIGINAL_LANGUAGE_PREF_VAL_KOREAN,
            )
            setDefaultValue(MDConstants.originalLanguagePrefDefaults)
        }

        val blockedGroupsPref = EditTextPreference(screen.context).apply {
            key = MDConstants.getBlockedGroupsPrefKey(dexLang)
            title = helper.intl["block_group_by_uuid"]
            summary = helper.intl["block_group_by_uuid_summary"]

            setOnBindEditTextListener(helper::setupEditTextUuidValidator)
        }

        val blockedUploaderPref = EditTextPreference(screen.context).apply {
            key = MDConstants.getBlockedUploaderPrefKey(dexLang)
            title = helper.intl["block_uploader_by_uuid"]
            summary = helper.intl["block_uploader_by_uuid_summary"]

            setOnBindEditTextListener(helper::setupEditTextUuidValidator)
        }

        val altTitlesInDescPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getAltTitlesInDescPrefKey(dexLang)
            title = helper.intl["alternative_titles_in_description"]
            summary = helper.intl["alternative_titles_in_description_summary"]
            setDefaultValue(false)
        }

        val preferExtensionLangTitlePref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getPreferExtensionLangTitlePrefKey(dexLang)
            title = helper.intl["prefer_title_in_extension_language"]
            summary = helper.intl["prefer_title_in_extension_language_summary"]
            setDefaultValue(true)
        }

        val finalChapterInDescPref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getFinalChapterInDescPrefKey(dexLang)
            title = helper.intl["final_chapter_in_description"]
            summary = helper.intl["final_chapter_in_description_summary"]
            setDefaultValue(true)
        }

        val includeUnavailablePref = SwitchPreferenceCompat(screen.context).apply {
            key = MDConstants.getIncludeUnavailablePrefKey(dexLang)
            title = helper.intl["include_unavailable"]
            summary = helper.intl["include_unavailable_summary"]
            setDefaultValue(false)
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

    private fun HttpUrl.Builder.addQueryParameter(name: String, values: Collection<String>?) = apply {
        values?.forEach { addQueryParameter(name, it) }
    }

    private val SharedPreferences.contentRating
        get() = getStringSet(MDConstants.getContentRatingPrefKey(dexLang), MDConstants.contentRatingPrefDefaults)

    private val SharedPreferences.originalLanguages: Set<String>
        get() {
            val originalLanguages = getStringSet(MDConstants.getOriginalLanguagePrefKey(dexLang), MDConstants.originalLanguagePrefDefaults).orEmpty()

            // dex has zh and zh-hk for chinese manhua
            return if (MDConstants.ORIGINAL_LANGUAGE_PREF_VAL_CHINESE in originalLanguages) {
                originalLanguages + MDConstants.ORIGINAL_LANGUAGE_PREF_VAL_CHINESE_HK
            } else {
                originalLanguages
            }
        }

    private val SharedPreferences.coverQuality
        get() = getString(MDConstants.getCoverQualityPreferenceKey(dexLang), "")

    private val SharedPreferences.tryUsingFirstVolumeCover
        get() = getBoolean(MDConstants.getTryUsingFirstVolumeCoverPrefKey(dexLang), MDConstants.TRY_USING_FIRST_VOLUME_COVER_DEFAULT)

    private val SharedPreferences.blockedGroups
        get() = getUuidList(MDConstants.getBlockedGroupsPrefKey(dexLang))

    private val SharedPreferences.blockedUploaders
        get() = getUuidList(MDConstants.getBlockedUploaderPrefKey(dexLang))

    private fun SharedPreferences.getUuidList(key: String): Set<String> = getString(key, "").orEmpty()
        .split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    private val SharedPreferences.forceStandardHttps
        get() = getBoolean(MDConstants.getStandardHttpsPreferenceKey(dexLang), false)

    private val SharedPreferences.useDataSaver
        get() = getBoolean(MDConstants.getDataSaverPreferenceKey(dexLang), false)

    private val SharedPreferences.altTitlesInDesc
        get() = getBoolean(MDConstants.getAltTitlesInDescPrefKey(dexLang), false)

    private val SharedPreferences.preferExtensionLangTitle
        get() = getBoolean(MDConstants.getPreferExtensionLangTitlePrefKey(dexLang), true)

    private val SharedPreferences.finalChapterInDesc
        get() = getBoolean(MDConstants.getFinalChapterInDescPrefKey(dexLang), true)

    private val SharedPreferences.includeUnavailable
        get() = getBoolean(MDConstants.getIncludeUnavailablePrefKey(dexLang), false)

    /**
     * Previous versions of the extension allowed invalid UUID values to be stored in the
     * preferences. This method clear invalid UUIDs in case the user have updated from
     * a previous version with that behaviour.
     */
    private fun SharedPreferences.sanitizeExistingUuidPrefs() {
        if (getBoolean(MDConstants.getHasSanitizedUuidsPrefKey(dexLang), false)) {
            return
        }

        fun sanitized(key: String) = getString(key, "").orEmpty()
            .split(",")
            .map(String::trim)
            .filter(MDConstants.uuidRegex::matches)
            .joinToString(", ")

        edit()
            .putString(MDConstants.getBlockedGroupsPrefKey(dexLang), sanitized(MDConstants.getBlockedGroupsPrefKey(dexLang)))
            .putString(MDConstants.getBlockedUploaderPrefKey(dexLang), sanitized(MDConstants.getBlockedUploaderPrefKey(dexLang)))
            .putBoolean(MDConstants.getHasSanitizedUuidsPrefKey(dexLang), true)
            .apply()
    }
}
