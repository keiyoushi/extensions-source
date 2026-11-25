package eu.kanade.tachiyomi.extension.all.mangadex

import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.applicationContext
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.parser.Parser
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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

    private val translatedLanguages = if (dexLang == ALL_LANGUAGES) emptySet() else setOf(dexLang)

    private val intl = Intl(
        language = lang,
        baseLanguage = ENGLISH,
        availableLanguages = setOf(ENGLISH, "pt-BR", "pt", "es", "es-419", "ru"),
        classLoader = this::class.java.classLoader!!,
        createMessageFileName = {
            when (it) {
                "es-419" -> Intl.createDefaultMessageFileName("es")
                "pt" -> Intl.createDefaultMessageFileName("pt-BR")
                else -> Intl.createDefaultMessageFileName(it)
            }
        },
    )

    private val tagNames by lazy { getTags(intl).associate { it.id to it.name } }

    private val preferences by getPreferencesLazy {
        migrateContentRating()
        migratePreferredTitle()
        migrateObsoletePrefs()
    }

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
                    .format(dateFormatterNoOffset),
            )
            .build()

        return client.get(url, Fetch.XHR).toMangasPage()
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(latestUpdatesUrl(page), Fetch.XHR, CacheControl.FORCE_NETWORK).toChapterMangasPage()

    /**
     * Used by Komikku
     */
    fun latestUpdatesRequest(page: Int): Request = GET(latestUpdatesUrl(page), headers, CacheControl.FORCE_NETWORK).newBuilder().fetch(Fetch.XHR).build()

    /**
     * Used by Komikku
     */
    fun latestUpdatesParse(response: Response): MangasPage = runBlocking { response.toChapterMangasPage() }

    private fun latestUpdatesUrl(page: Int, uploader: String? = null): HttpUrl = API_CHAPTER_URL.toHttpUrl().newBuilder()
        .addQueryParameter("offset", (LATEST_CHAPTER_LIMIT * (page - 1)).toString())
        .addQueryParameter("limit", LATEST_CHAPTER_LIMIT.toString())
        .addQueryParameter("translatedLanguage[]", translatedLanguages)
        .addQueryParameter("order[publishAt]", "desc")
        .addQueryParameter("includeFutureUpdates", if (isDelegate()) "1" else "0")
        .addQueryParameter("includeFuturePublishAt", "0")
        .addQueryParameter("includeEmptyPages", "0")
        .apply { uploader?.let { addQueryParameter("uploader", it) } }
        .addQueryParameter("originalLanguage[]", preferences.originalLanguages)
        .addQueryParameter("contentRating[]", preferences.contentRating)
        .addQueryParameter("excludedGroups[]", defaultBlockedGroups + preferences.blockedGroups)
        .addQueryParameter("excludedUploaders[]", preferences.blockedUploaders)
        .build()

    /** Manga of the chapters, in the order of the chapters. */
    private suspend fun Response.toChapterMangasPage(): MangasPage {
        val chapters = parseAs<PagedDto<ChapterDto>>()

        return MangasPage(getMangaByIds(chapters.data.map { it.mangaId }.distinct()), chapters.hasNextPage)
    }

    private suspend fun getMangaByIds(mangaIds: List<String>): List<SManga> {
        if (mangaIds.isEmpty()) return emptyList()

        val url = API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", COVER_ART)
            .addQueryParameter("limit", mangaIds.size.toString())
            .addQueryParameter("contentRating[]", preferences.contentRating)
            .addQueryParameter("ids[]", mangaIds)
            .build()
        val mangaList = client.get(url, Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<PagedDto<MangaDto>>().data

        return mangaList.orderedBy(mangaIds).toSMangaList()
    }

    private fun List<MangaDto>.orderedBy(mangaIds: List<String>): List<MangaDto> {
        val byId = associateBy { it.id }
        return mangaIds.mapNotNull(byId::get)
    }

    // Search

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.endsWith("mangadex.org")) {
            val searchPrefix = url.pathSegments.firstOrNull()?.let { pathToSearchPrefix[it] }
            val uuid = uuidRegex.find(url.toString())?.value
            if (searchPrefix != null && uuid != null) {
                return getSearchMangaList(page, searchPrefix + uuid, getFilterList())
            }
        }
        return getSearchMangaList(page, url.toString(), getFilterList())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = when {
        query.startsWith(PREFIX_ID_SEARCH) ->
            getMangaByIdPage(query.removePrefix(PREFIX_ID_SEARCH))

        query.startsWith(PREFIX_CH_SEARCH) ->
            getMangaByIdPage(getMangaIdFromChapterId(query.removePrefix(PREFIX_CH_SEARCH)))

        query.startsWith(PREFIX_USER_SEARCH) ->
            client.get(latestUpdatesUrl(page, uploader = query.removePrefix(PREFIX_USER_SEARCH)), Fetch.XHR, CacheControl.FORCE_NETWORK).toChapterMangasPage()

        query.startsWith(PREFIX_LIST_SEARCH) ->
            getListPage(query.removePrefix(PREFIX_LIST_SEARCH), page, filters)

        else -> {
            val url = mangaListUrl(page)

            when {
                query.startsWith(PREFIX_GRP_SEARCH) ->
                    url.addQueryParameter("group", query.removePrefix(PREFIX_GRP_SEARCH).requireUuid("invalid_group_id"))

                query.startsWith(PREFIX_AUTHOR_SEARCH) ->
                    url.addQueryParameter("authorOrArtist", query.removePrefix(PREFIX_AUTHOR_SEARCH).requireUuid("invalid_author_id"))

                else -> query.replace(whitespaceRegex, " ")
                    .takeIf(String::isNotEmpty)
                    ?.let { url.addQueryParameter("title", it) }
            }

            if (!query.startsWith(PREFIX_AUTHOR_SEARCH)) {
                addAuthorFilter(url, filters)
            }

            client.get(url.addFilters(filters).build(), Fetch.XHR, CacheControl.FORCE_NETWORK).toMangasPage()
        }
    }

    private suspend fun getMangaByIdPage(mangaId: String): MangasPage {
        val url = API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("ids[]", mangaId.requireUuid("invalid_manga_id"))
            .addQueryParameter("includes[]", COVER_ART)
            .addQueryParameter("contentRating[]", allContentRatings)
            .build()

        return client.get(url, Fetch.XHR, CacheControl.FORCE_NETWORK).toMangasPage()
    }

    private suspend fun getMangaIdFromChapterId(id: String): String {
        val response = client.get("$API_CHAPTER_URL/$id", Fetch.XHR, ensureSuccess = false)

        if (!response.isSuccessful) {
            response.close()
            throw Exception(intl.format("unable_to_process_chapter_request", response.code))
        }

        return response.parseAs<DataDto<ChapterDto>>().data.mangaId
    }

    private suspend fun getListPage(listId: String, page: Int, filters: FilterList): MangasPage {
        val mangaIds = client.get("$API_LIST_URL/$listId", Fetch.XHR, CacheControl.FORCE_NETWORK)
            .parseAs<DataDto<CustomListDto>>()
            .data.relationships
            .filter { it.type == MANGA }
            .map { it.id }

        if (mangaIds.isEmpty()) {
            throw Exception(intl["no_series_in_list"])
        }

        val offset = (page - 1) * MANGA_LIMIT
        val url = API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("limit", MANGA_LIMIT.toString())
            .addQueryParameter("includes[]", COVER_ART)
            .addQueryParameter("ids[]", mangaIds.drop(offset).take(MANGA_LIMIT))
        addAuthorFilter(url, filters)

        val mangaList = client.get(url.addFilters(filters).build(), Fetch.XHR, CacheControl.FORCE_NETWORK)
            .parseAs<PagedDto<MangaDto>>().data

        return MangasPage(mangaList.toSMangaList(), offset + MANGA_LIMIT < mangaIds.size)
    }

    private suspend fun addAuthorFilter(url: HttpUrl.Builder, filters: FilterList) {
        val name = filters.firstInstanceOrNull<AuthorFilter>()?.state?.trim()
        if (name.isNullOrEmpty()) return

        val authorUrl = API_AUTHOR_URL.toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("limit", "1")
            .build()
        val authorId = client.get(authorUrl, Fetch.XHR).parseAs<PagedDto<AuthorDto>>().data.firstOrNull()?.id
            ?: throw Exception(intl.format("author_not_found", name))

        url.addQueryParameter("authorOrArtist", authorId)
    }

    private fun String.requireUuid(errorKey: String): String = uuidRegex.find(this)?.value
        ?: throw Exception(intl[errorKey])

    // Manga lists

    private fun mangaListUrl(page: Int): HttpUrl.Builder = API_MANGA_URL.toHttpUrl().newBuilder()
        .addQueryParameter("limit", MANGA_LIMIT.toString())
        .addQueryParameter("offset", (MANGA_LIMIT * (page - 1)).toString())
        .addQueryParameter("includes[]", COVER_ART)

    private suspend fun Response.toMangasPage(): MangasPage {
        val mangaList = parseAs<PagedDto<MangaDto>>()

        return MangasPage(mangaList.data.toSMangaList(), mangaList.hasNextPage)
    }

    private suspend fun List<MangaDto>.toSMangaList(): List<SManga> {
        val firstVolumeCovers = fetchFirstVolumeCovers(this)
        return map { it.toSManga(firstVolumeCovers[it.id]) }
    }

    private fun MangaDto.toSManga(firstVolumeCover: String?): SManga = SManga.create().apply {
        url = "/manga/$id"
        title = resolveTitle(attributes).removeEntities()

        thumbnail_url = (firstVolumeCover ?: coverFileName)?.let { "$CDN_URL/covers/$id/$it${preferences.coverQuality}" }
    }

    private fun resolveTitle(attr: MangaAttributesDto): String {
        val preferredTitle = preferences.preferredTitle
        val romanizedTitle = romanizedLangCodes[attr.originalLanguage]
            ?.takeIf { preferredTitle == TITLE_PREF_VAL_ROMANIZED }
            ?.let { attr.title[it] ?: attr.altTitles.findTitleByLang(it) }

        return romanizedTitle ?: attr.title[dexLang] ?: run {
            val mainTitle = attr.title.values.firstOrNull()
            val langTitle = attr.altTitles.findTitleByLang(dexLang)
            val enTitle = attr.altTitles.findTitleByLang("en")

            if (preferredTitle == TITLE_PREF_VAL_EXTENSION) {
                langTitle ?: mainTitle ?: enTitle
            } else {
                mainTitle ?: langTitle ?: enTitle
            }
        }.orEmpty()
    }

    private fun List<LocalizedString>.findTitleByLang(lang: String): String? = firstNotNullOfOrNull { it[lang] }

    /** Turns HTML entities in names and descriptions into characters, e.g. &hearts; into ♥. */
    private fun String.removeEntities(): String = Parser.unescapeEntities(this, false)

    // Covers

    /** The first cover of each manga in its original language. */
    private suspend fun fetchFirstVolumeCovers(mangaList: List<MangaDto>): Map<String, String> {
        if (!preferences.tryUsingFirstVolumeCover || mangaList.isEmpty()) return emptyMap()

        val originalLanguages = mangaList.associate { it.id to it.attributes.originalLanguage }
        val locales = originalLanguages.values.distinct()

        val url = "$API_URL/cover".toHttpUrl().newBuilder()
            .addQueryParameter("order[volume]", "asc")
            .addQueryParameter("manga[]", originalLanguages.keys)
            .addQueryParameter("locales[]", locales)
            .addQueryParameter("limit", (mangaList.size * locales.size).coerceAtMost(MANGA_IDS_LIMIT).toString())
            .build()

        return client.get(url, Fetch.XHR).parseAs<PagedDto<CoverDto>>().data
            .filter { it.attributes.locale == originalLanguages[it.mangaId] }
            .distinctBy { it.mangaId }
            .associate { it.mangaId to it.attributes.fileName }
    }

    // Details

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.replace("/manga/", "/title/") + "/" + titleToSlug(manga.title)

    private fun titleToSlug(title: String) = title.trim()
        .lowercase(Locale.US)
        .replace(titleSpecialCharactersRegex, "-")
        .replace(trailingHyphenRegex, "")
        .split("-")
        .reduce { accumulator, element ->
            val currentSlug = "$accumulator-$element"
            if (currentSlug.length > 100) {
                accumulator
            } else {
                currentSlug
            }
        }

    private suspend fun getMangaDetails(manga: SManga): SManga = coroutineScope {
        val mangaId = manga.url.uuid()

        val aggregate = async {
            val url = "$API_MANGA_URL/$mangaId/aggregate".toHttpUrl().newBuilder()
                .addQueryParameter("translatedLanguage[]", translatedLanguages)
                .build()
            client.get(url, Fetch.XHR).parseAs<AggregateDto>().volumes
        }

        val url = "$API_MANGA_URL/$mangaId".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", COVER_ART)
            .addQueryParameter("includes[]", AUTHOR)
            .addQueryParameter("includes[]", ARTIST)
            .build()
        val mangaDto = client.get(url, Fetch.XHR, CacheControl.FORCE_NETWORK).parseAs<DataDto<MangaDto>>().data
        val firstVolumeCover = fetchFirstVolumeCovers(listOf(mangaDto))[mangaDto.id]

        mangaDto.toDetailedSManga(aggregate.await(), firstVolumeCover)
    }

    private fun MangaDto.toDetailedSManga(volumes: Map<String, AggregateVolumeDto>, firstVolumeCover: String?): SManga {
        val attr = attributes

        // Things that will go with the genre tags but aren't actually genre
        val dexLocale = Locale.forLanguageTag(dexLang)

        val nonGenres = listOfNotNull(
            attr.publicationDemographic
                ?.let { intl["publication_demographic_$it"] },
            attr.contentRating
                .takeIf { it != "safe" }
                ?.let { intl.format("content_rating_genre", intl["content_rating_$it"]) },
            Locale.forLanguageTag(attr.originalLanguage)
                .getDisplayName(dexLocale)
                .replaceFirstChar { it.uppercase(dexLocale) },
        )

        val genresMap = attr.tags
            .groupBy({ it.group }) { tagNames[it.id] }
            .mapValues { it.value.filterNotNull().sortedWith(intl.collator) }

        val genreList = tagGroupsOrder.flatMap { genresMap[it].orEmpty() } + nonGenres

        val altTitles = attr.altTitles
            .flatMap { it.values }
            .map { it.removeEntities() }

        return toSManga(firstVolumeCover).apply {
            description = MarkdownDescription.Builder()
                // unescaped, text like <Twilight> is dropped as an html tag
                .markdown((attr.description[dexLang] ?: attr.description["en"])?.removeEntities()?.replace(markdownTagRegex, "\\\\<"))
                .altTitles(altTitles, intl["alternative_titles"])
                .field(intl["final_chapter"], formatNumber(attr.lastVolume, attr.lastChapter))
                .build()
            author = relationships.names(AUTHOR)
            artist = relationships.names(ARTIST)
            status = getPublicationStatus(attr, volumes)
            genre = genreList
                .filter(String::isNotEmpty)
                .joinToString()
        }
    }

    private fun List<RelationshipDto>.names(type: String) = filter { it.type == type }
        .mapNotNull { it.attributes?.name }
        .distinct()
        .joinToString()

    private fun getPublicationStatus(attr: MangaAttributesDto, volumes: Map<String, AggregateVolumeDto>): Int {
        val hasLastChapter = volumes.values.any { volume -> volume.chapters.values.any { it.chapter == attr.lastChapter } }
        val isEnded = attr.status == "completed" || attr.status == "cancelled"

        val tagIds = attr.tags.map { it.id }
        val isOneShot = TAG_ONE_SHOT_UUID in tagIds && TAG_ANTHOLOGY_UUID !in tagIds

        return when {
            hasLastChapter && isEnded -> SManga.COMPLETED
            isOneShot && volumes["none"]?.chapters?.get("none") != null -> SManga.COMPLETED
            attr.status == "ongoing" -> SManga.ONGOING
            attr.status == "cancelled" -> SManga.CANCELLED
            attr.status == "completed" -> SManga.PUBLISHING_FINISHED
            attr.status == "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun formatNumber(volume: String?, chapter: String?) = listOfNotNull(
        volume?.takeIf { it.isNotEmpty() }?.let { "Vol.$it" },
        chapter?.takeIf { it.isNotEmpty() }?.let { "Ch.$it" },
    ).joinToString(" ")

    // Related

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val relatedIds = client.get("$API_MANGA_URL/${manga.url.uuid()}", Fetch.XHR)
            .parseAs<DataDto<MangaDto>>()
            .data.relationships
            .filter { it.related != null }
            .sortedBy { relatedMangaOrder.indexOf(it.related).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
            .map { it.id }
            .distinct()

        return getMangaByIds(relatedIds.take(MANGA_IDS_LIMIT))
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
            .map { it.toSChapter() }
    }

    private fun ChapterDto.toSChapter(): SChapter {
        val attr = attributes

        val groups = relationships
            .filter { it.type == SCANLATION_GROUP && it.id != LEGACY_NO_GROUP_ID }
            .mapNotNull { it.attributes?.name }
            .joinToString(" & ")
        val uploaders = relationships
            .filter { it.type == USER }
            .mapNotNull { it.attributes?.username }
            .joinToString(" & ")
        val credits = when {
            groups.isNotEmpty() -> groups
            uploaders.isNotEmpty() -> intl.format("uploaded_by", uploaders)
            else -> intl["no_group"]
        }

        val unavailablePrefix = if (attr.isUnavailable) "${intl["chapter_unavailable_prefix"]} - " else ""
        val languagePrefix = if (dexLang == ALL_LANGUAGES) "[${attr.translatedLanguage}] " else ""

        return SChapter.create().apply {
            url = "/chapter/$id"
            name = listOf(formatNumber(attr.volume, attr.chapter), attr.title.orEmpty())
                .filter { it.isNotEmpty() }
                .joinToString(" - ")
                .ifEmpty { "Oneshot" }
                .removeEntities()
            date_upload = Instant.tryParse(attr.publishAt)
            scanlator = languagePrefix + unavailablePrefix + credits
            if (attr.isUnavailable) {
                memo = buildJsonObject { put(MEMO_UNAVAILABLE, true) }
            }
        }
    }

    private fun chapterFeedUrl(mangaId: String, offset: Int): HttpUrl = "$API_MANGA_URL/$mangaId/feed".toHttpUrl().newBuilder()
        .addQueryParameter("includes[]", SCANLATION_GROUP)
        .addQueryParameter("includes[]", USER)
        .addQueryParameter("limit", CHAPTER_FEED_LIMIT.toString())
        .addQueryParameter("offset", offset.toString())
        .addQueryParameter("order[volume]", "desc")
        .addQueryParameter("order[chapter]", "desc")
        .addQueryParameter("includeFuturePublishAt", "0")
        .addQueryParameter("translatedLanguage[]", translatedLanguages)
        .addQueryParameter("contentRating[]", allContentRatings)
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
    ): SMangaUpdate = SMangaUpdate(
        manga = { if (fetchDetails) getMangaDetails(manga) else manga },
        chapters = { if (fetchChapters) getChapterList(manga) else chapters },
    )

    /** Throws for urls from MangaDex v3, which used numeric ids, so people migrate. */
    private fun String.uuid(): String = uuidRegex.find(this)?.value
        ?: throw Exception(intl["migrate_warning"])

    // Pages

    /** MD@Home base urls by the at-home url they came from, reused until they expire. */
    private val atHomeHosts = ConcurrentHashMap<String, AtHomeHost>()

    private class AtHomeHost(val url: String, val fetchedAt: Long)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // the API has no pages for these, so don't hit it for every chapter of a download queue
        if (chapter.memo[MEMO_UNAVAILABLE]?.booleanOrNull == true) {
            throw Exception(intl["chapter_unavailable"])
        }

        val atHomeUrl = "$API_URL/at-home/server/${chapter.url.uuid()}" +
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
        val validHost = if (System.currentTimeMillis() - fetchedAt.toLong() < mdAtHomeTokenLifespan) host else atHomeHost(atHomeUrl)
        return GET(validHost + page.imageUrl, headers)
    }

    /** MD@Home urls expire, so pages loaded later need a fresh one. */
    private fun atHomeHost(atHomeUrl: String): String {
        val now = System.currentTimeMillis()
        atHomeHosts[atHomeUrl]
            ?.takeIf { now - it.fetchedAt < mdAtHomeTokenLifespan }
            ?.let { return it.url }

        val host = client.newCall(GET(atHomeUrl, headers, CacheControl.FORCE_NETWORK).newBuilder().fetch(Fetch.XHR).build())
            .execute()
            .parseAs<AtHomeDto>().baseUrl
        atHomeHosts[atHomeUrl] = AtHomeHost(host, now)
        return host
    }

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addList(
            COVER_QUALITY_PREF,
            "cover_quality",
            listOf("" to intl["cover_quality_original"], ".512.jpg" to intl["cover_quality_medium"], ".256.jpg" to intl["cover_quality_low"]),
            default = "",
        )
        screen.addSwitch(TRY_USING_FIRST_VOLUME_COVER_PREF, "try_using_first_volume_cover")
        screen.addSwitch(DATA_SAVER_PREF, "data_saver")
        screen.addSwitch(STANDARD_HTTPS_PORT_PREF, "standard_https_port")
        screen.addList(
            TITLE_PREF,
            "preferred_title",
            listOf(TITLE_PREF_VAL_EXTENSION, TITLE_PREF_VAL_ROMANIZED, TITLE_PREF_VAL_MAIN).map { it to intl["preferred_title_$it"] },
            default = TITLE_PREF_DEFAULT,
        ).setOnPreferenceChangeListener { _, newValue ->
            // TachiyomiSY and Komikku read the old switch when delegating
            preferences.edit()
                .putBoolean("${PREFER_EXTENSION_LANG_TITLE_PREF}_$dexLang", newValue == TITLE_PREF_VAL_EXTENSION)
                .apply()
            true
        }
        screen.addSwitch(INCLUDE_UNAVAILABLE_PREF, "include_unavailable")
        screen.addList(
            CONTENT_RATING_PREF,
            "standard_content_rating",
            allContentRatings.map { it to intl.format("content_rating_up_to", intl["content_rating_$it"]) },
            default = CONTENT_RATING_PREF_DEFAULT,
        )

        MultiSelectListPreference(screen.context).apply {
            key = "${ORIGINAL_LANGUAGE_PREF}_$dexLang"
            title = intl["filter_original_languages"]
            summary = intl["filter_original_languages_summary"]
            entryValues = arrayOf("ja", "zh", "ko")
            entries = entryValues.map { intl.languageDisplayName(it.toString()) }.toTypedArray()
            setDefaultValue(emptySet<String>())
        }.also(screen::addPreference)

        screen.addUuidList(BLOCKED_GROUPS_PREF, "block_group_by_uuid")
        screen.addUuidList(BLOCKED_UPLOADER_PREF, "block_uploader_by_uuid")
    }

    private fun PreferenceScreen.addList(pref: String, name: String, options: List<Pair<String, String>>, default: String) = ListPreference(context).apply {
        key = "${pref}_$dexLang"
        title = intl[name]
        entryValues = options.map { it.first }.toTypedArray()
        entries = options.map { it.second }.toTypedArray()
        setDefaultValue(default)
        summary = "%s"
    }.also(::addPreference)

    private fun PreferenceScreen.addSwitch(pref: String, name: String) = SwitchPreferenceCompat(context).apply {
        key = "${pref}_$dexLang"
        title = intl[name]
        summary = intl["${name}_summary"]
        setDefaultValue(false)
    }.also(::addPreference)

    private fun PreferenceScreen.addUuidList(pref: String, name: String) = EditTextPreference(context).apply {
        key = "${pref}_$dexLang"
        title = intl[name]
        summary = intl["${name}_summary"]
        setOnBindEditTextListener(::setupEditTextUuidValidator)
    }.also(::addPreference)

    /**
     * Adds a custom [TextWatcher] to the preference's [EditText] that show an
     * error if the input value contains invalid UUIDs. If the validation fails,
     * the Ok button is disabled to prevent the user from saving the value.
     */
    private fun setupEditTextUuidValidator(editText: EditText) {
        editText.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(editable: Editable?) {
                    requireNotNull(editable)

                    val text = editable.toString()

                    val isValid = text.isBlank() || text
                        .split(",")
                        .map(String::trim)
                        .all(uuidRegex::matches)

                    editText.error = if (!isValid) intl["invalid_uuids"] else null
                    editText.rootView.findViewById<Button>(android.R.id.button1)
                        ?.isEnabled = editText.error == null
                }
            },
        )
    }

    override fun getFilterList(data: JsonElement?): FilterList = createFilterList(intl, preferences.originalLanguages, preferences.contentRating)

    private fun HttpUrl.Builder.addFilters(filters: FilterList) = apply {
        filters.filterIsInstance<UrlQueryFilter>().forEach { it.addQueryParameter(this, dexLang) }
    }

    private fun HttpUrl.Builder.addQueryParameter(name: String, values: Collection<String>?) = apply {
        values?.forEach { addQueryParameter(name, it) }
    }

    private val SharedPreferences.contentRating: List<String>
        get() {
            val level = getString("${CONTENT_RATING_PREF}_$dexLang", CONTENT_RATING_PREF_DEFAULT)
            return allContentRatings.take(allContentRatings.indexOf(level) + 1)
        }

    private val SharedPreferences.originalLanguages: Set<String>
        get() {
            val originalLanguages = getStringSet("${ORIGINAL_LANGUAGE_PREF}_$dexLang", emptySet<String>()).orEmpty()

            // dex has zh and zh-hk for chinese manhua
            return if ("zh" in originalLanguages) {
                originalLanguages + "zh-hk"
            } else {
                originalLanguages
            }
        }

    private val SharedPreferences.coverQuality
        get() = getString("${COVER_QUALITY_PREF}_$dexLang", "").orEmpty()

    private val SharedPreferences.tryUsingFirstVolumeCover
        get() = getBoolean("${TRY_USING_FIRST_VOLUME_COVER_PREF}_$dexLang", false)

    private val SharedPreferences.blockedGroups
        get() = getUuidList("${BLOCKED_GROUPS_PREF}_$dexLang")

    private val SharedPreferences.blockedUploaders
        get() = getUuidList("${BLOCKED_UPLOADER_PREF}_$dexLang")

    private fun SharedPreferences.getUuidList(key: String): Set<String> = getString(key, "").orEmpty()
        .split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    private val SharedPreferences.forceStandardHttps
        get() = getBoolean("${STANDARD_HTTPS_PORT_PREF}_$dexLang", false)

    private val SharedPreferences.useDataSaver
        get() = getBoolean("${DATA_SAVER_PREF}_$dexLang", false)

    private val SharedPreferences.preferredTitle
        get() = getString("${TITLE_PREF}_$dexLang", TITLE_PREF_DEFAULT)!!

    private val SharedPreferences.includeUnavailable
        get() = getBoolean("${INCLUDE_UNAVAILABLE_PREF}_$dexLang", false)

    /**
     * Alt titles and the final chapter are always in the description now. TachiyomiSY and Komikku
     * still read these toggles when delegating, so turn them on there too.
     */
    private fun SharedPreferences.migrateObsoletePrefs() {
        val alwaysOn = listOf("altTitlesInDesc", "finalChapterInDesc")
            .map { "${it}_$dexLang" }
            .filterNot { getBoolean(it, false) }
        val sanitizedUuidsKey = "hasSanitizedUuids_$dexLang"
        if (alwaysOn.isEmpty() && sanitizedUuidsKey !in this) return

        val editor = edit().remove(sanitizedUuidsKey)
        alwaysOn.forEach { editor.putBoolean(it, true) }
        editor.apply()
    }

    /** The content rating used to be a multi-select; keep the highest rating that was chosen. */
    private fun SharedPreferences.migrateContentRating() {
        val legacyKey = "${LEGACY_CONTENT_RATING_PREF}_$dexLang"
        val legacy = getStringSet(legacyKey, null) ?: return

        val editor = edit().remove(legacyKey)
        allContentRatings.lastOrNull { it in legacy }
            ?.let { editor.putString("${CONTENT_RATING_PREF}_$dexLang", it) }
        editor.apply()
    }

    /** The preferred title used to be a switch, on by default, between the extension's language and the main title. */
    private fun SharedPreferences.migratePreferredTitle() {
        val key = "${TITLE_PREF}_$dexLang"
        if (key in this || getBoolean("${PREFER_EXTENSION_LANG_TITLE_PREF}_$dexLang", true)) return

        edit().putString(key, TITLE_PREF_VAL_MAIN).apply()
    }
}

const val ALL_LANGUAGES = "all"
const val ENGLISH = "en"

private const val MANGA_LIMIT = 20
private const val MANGA_IDS_LIMIT = 100
private const val LATEST_CHAPTER_LIMIT = 100
private const val CHAPTER_FEED_LIMIT = 500

private const val CDN_URL = "https://uploads.mangadex.org"
private const val API_URL = "https://api.mangadex.org"
private const val API_MANGA_URL = "$API_URL/manga"
private const val API_CHAPTER_URL = "$API_URL/chapter"
private const val API_LIST_URL = "$API_URL/list"
private const val API_AUTHOR_URL = "$API_URL/author"

private const val MEMO_UNAVAILABLE = "unavailable"

/** "No group" left over from MDv3. */
private const val LEGACY_NO_GROUP_ID = "00e03853-1b96-4f41-9542-c71b8692033b"

private const val PREFIX_ID_SEARCH = "id:"
private const val PREFIX_CH_SEARCH = "ch:"
private const val PREFIX_GRP_SEARCH = "grp:"
private const val PREFIX_AUTHOR_SEARCH = "author:"
private const val PREFIX_USER_SEARCH = "usr:"
private const val PREFIX_LIST_SEARCH = "list:"

private const val COVER_QUALITY_PREF = "thumbnailQuality"
private const val DATA_SAVER_PREF = "dataSaverV5"
private const val STANDARD_HTTPS_PORT_PREF = "usePort443"
private const val LEGACY_CONTENT_RATING_PREF = "contentRating"
private const val CONTENT_RATING_PREF = "contentRatingLevel"
private const val CONTENT_RATING_PREF_DEFAULT = "erotica"
private const val ORIGINAL_LANGUAGE_PREF = "originalLanguage"
private const val BLOCKED_GROUPS_PREF = "blockedGroups"
private const val BLOCKED_UPLOADER_PREF = "blockedUploader"
private const val TRY_USING_FIRST_VOLUME_COVER_PREF = "tryUsingFirstVolumeCover"
private const val TITLE_PREF = "preferredTitle"
private const val TITLE_PREF_VAL_EXTENSION = "extension"
private const val TITLE_PREF_VAL_ROMANIZED = "romanized"
private const val TITLE_PREF_VAL_MAIN = "main"
private const val TITLE_PREF_DEFAULT = TITLE_PREF_VAL_EXTENSION
private const val PREFER_EXTENSION_LANG_TITLE_PREF = "preferExtensionLangTitle"
private const val INCLUDE_UNAVAILABLE_PREF = "includeUnavailable"

private const val TAG_ANTHOLOGY_UUID = "51d83883-4103-437c-b4b1-731cb73d786c"
private const val TAG_ONE_SHOT_UUID = "0234a31e-a729-4e28-9d6a-3f87c4966b9e"

private val tagGroupsOrder = listOf("content", "format", "genre", "theme")
private val mdAtHomeTokenLifespan = 5.minutes.inWholeMilliseconds

private val pathToSearchPrefix = mapOf(
    "manga" to PREFIX_ID_SEARCH,
    "title" to PREFIX_ID_SEARCH,
    "chapter" to PREFIX_CH_SEARCH,
    "group" to PREFIX_GRP_SEARCH,
    "author" to PREFIX_AUTHOR_SEARCH,
    "user" to PREFIX_USER_SEARCH,
    "list" to PREFIX_LIST_SEARCH,
)

private val defaultBlockedGroups = setOf(
    "5fed0576-8b94-4f9a-b6a7-08eecd69800d", // Azuki
    "06a9fecb-b608-4f19-b93c-7caab06b7f44", // Bilibili
    "8d8ecf83-8d42-4f8c-add8-60963f9f28d9", // Comikey
    "caa63201-4a17-4b7f-95ff-ed884a2b7e60", // INKR
    "319c1b10-cbd0-4f55-a46e-c4ee17e65139", // Manga Hot
    "4f1de6a2-f0c5-4ac5-bce5-02c7dbb67deb", // MANGA Plus
)

/** Order of related manga by how they're related; unlisted ones go last. */
private val relatedMangaOrder = listOf(
    "main_story",
    "prequel",
    "sequel",
    "side_story",
    "spin_off",
    "alternate_story",
    "alternate_version",
    "adapted_from",
    "based_on",
    "preserialization",
    "serialization",
    "colored",
    "monochrome",
    "same_franchise",
    "shared_universe",
    "doujinshi",
)

private val romanizedLangCodes = mapOf(
    "ja" to "ja-ro",
    "ko" to "ko-ro",
    "zh" to "zh-ro",
    "zh-hk" to "zh-ro",
)

private val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
private val markdownTagRegex = "<(?!https?://)".toRegex()
private val whitespaceRegex = "\\s".toRegex()
private val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
private val trailingHyphenRegex = "-+$".toRegex()
