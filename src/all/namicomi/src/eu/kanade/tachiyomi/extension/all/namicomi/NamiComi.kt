package eu.kanade.tachiyomi.extension.all.namicomi

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.namicomi.dto.AbstractTagDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ChapterDataDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.ContentRatingDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.CoverArtDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityAccessMapDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityAccessRequestDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityAccessRequestItemDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.EntityDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.MangaDataDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.MangaListDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.OrganizationDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.PageListDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.PaginatedResponseDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.RefreshTokenResponse
import eu.kanade.tachiyomi.extension.all.namicomi.dto.StatusDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.TagDto
import eu.kanade.tachiyomi.extension.all.namicomi.dto.Token
import eu.kanade.tachiyomi.extension.all.namicomi.dto.UnknownEntity
import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getBoolean
import keiyoushi.utils.getBooleanOrNull
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.getPreferences
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.Locale
import kotlin.collections.orEmpty
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class NamiComi :
    KeiSource(),
    ConfigurableSource {

    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain"
    private val cdnUrl get() = "https://uploads.$domain"

    private val extLang: String
        get() = when (lang) {
            "zh-Hans" -> "zh-hans"
            "zh-Hant" -> "zh-hant"
            "pt-BR" -> "pt-br"
            "pt" -> "pt-pt"
            "es" -> "es-es"
            else -> lang
        }

    private val json = Json(jsonInstance) {
        serializersModule += SerializersModule {
            polymorphic(EntityDto::class) {
                defaultDeserializer { UnknownEntity.serializer() }
            }
        }
    }

    private val preferences = getPreferences()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { !it.encodedPath.contains("/covers/") }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/title/search".toHttpUrl().newBuilder()
            .addQueryParameter("order[views]", "desc")
            .addQueryParameter("availableTranslatedLanguages[]", extLang)
            .addQueryParameter("limit", MANGA_LIMIT.toString())
            .addQueryParameter("offset", (MANGA_LIMIT * (page - 1)).toString())
            .addCommonIncludeParameters()
            .addCommonTypeParameters()
            .build()
        val response = client.get(url)

        return mangaListParse(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/title/search".toHttpUrl().newBuilder()
            .addQueryParameter("order[publishedAt]", "desc")
            .addQueryParameter("availableTranslatedLanguages[]", extLang)
            .addQueryParameter("limit", MANGA_LIMIT.toString())
            .addQueryParameter("offset", (MANGA_LIMIT * (page - 1)).toString())
            .addCommonIncludeParameters()
            .addCommonTypeParameters()
            .build()
        val response = client.get(url)

        return mangaListParse(response)
    }

    private fun mangaListParse(response: Response): MangasPage {
        val mangaListDto = response.parseAs<MangaListDto>(json)
        val mangaList = mangaListDto.data.map { it.toSManga() }

        return MangasPage(mangaList, mangaListDto.meta.hasNextPage)
    }

    private fun MangaDataDto.toSManga(): SManga {
        val attr = attributes!!
        val extLocale = Locale.forLanguageTag(extLang)

        return SManga.create().apply {
            initialized = true
            url = id
            description = attr.description[lang] ?: attr.description["en"]
            author = relationships
                .filterIsInstance<OrganizationDto>()
                .mapNotNull { it.attributes?.name }
                .distinct()
                .joinToString()
            status = when (attr.publicationStatus) {
                StatusDto.ONGOING -> SManga.ONGOING
                StatusDto.CANCELLED -> SManga.CANCELLED
                StatusDto.COMPLETED -> SManga.COMPLETED
                StatusDto.HIATUS -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            genre = buildList {
                val genresMap = relationships
                    .filterIsInstance<AbstractTagDto>()
                    .groupBy({ it.attributes!!.group }) { tagDto ->
                        tagDto.attributes!!.name[extLang] ?: tagDto.attributes!!.name["en"]
                    }
                    .mapValues { it.value.filterNotNull().sorted() }

                arrayOf("content-warnings", "format", "genre", "theme")
                    .flatMapTo(this) { genresMap[it].orEmpty() }

                attr.contentRating
                    .takeIf { it != ContentRatingDto.SAFE }
                    ?.also { add("Content Rating: $it") }

                attr.originalLanguage
                    ?.let { Locale.forLanguageTag(it) }
                    ?.getDisplayName(extLocale)
                    ?.replaceFirstChar { it.uppercase(extLocale) }
                    ?.also { add(it) }
            }.joinToString()

            attributes.title.let { titleMap ->
                title = titleMap[extLang] ?: titleMap["en"] ?: titleMap.values.first()
            }

            relationships
                .filterIsInstance<CoverArtDto>()
                .firstOrNull()
                ?.attributes?.fileName
                ?.also { coverFileName ->
                    val coverSuffix = preferences.coverQuality
                    thumbnail_url = when (!coverSuffix.isNullOrEmpty()) {
                        true -> "$cdnUrl/covers/$id/$coverFileName$coverSuffix"
                        else -> "$cdnUrl/covers/$id/$coverFileName"
                    }
                }
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/title/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", MANGA_LIMIT.toString())
            addQueryParameter("offset", (MANGA_LIMIT * (page - 1)).toString())
            addCommonIncludeParameters()
            addCommonTypeParameters()

            query.replace(whitespaceRegex, " ").trim().takeIf { it.isNotBlank() }?.also {
                addQueryParameter("title", it)
            }

            filters.filterIsInstance<UrlQueryFilter>()
                .forEach { filter -> filter.addQueryParameter(this, extLang) }
        }.build()

        val response = client.get(url)

        return mangaListParse(response)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val groupOrder = listOf("content-warnings", "format", "genre", "theme")

        val tags = client.get("$apiUrl/title/tags")
            .parseAs<PaginatedResponseDto<TagDto>>(json).data
            .groupBy { it.attributes!!.group }
            .filterKeys { it in groupOrder }
            .mapValues { (_, tagList) ->
                tagList
                    .map { it.id to (it.attributes!!.name[extLang] ?: it.attributes.name["en"]!!) }
                    .sortedBy { it.second }
            }
            .toList()
            .sortedBy { groupOrder.indexOf(it.first) }
            .toMap()

        return tags.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            HasAvailableChaptersFilter(),
            ContentRatingList(),
            StatusList(),
            SortFilter(),
        )

        data?.parseAs<Map<String, List<Pair<String, String>>>>()?.also { tagGroups ->
            filters.add(TagsFilterMode())
            val mapping = mapOf(
                "content-warnings" to "Content",
                "format" to "Format",
                "genre" to "Genre",
                "theme" to "Theme",
            )
            tagGroups.forEach { (group, tags) ->
                filters.add(
                    TagList(mapping[group]!!, tags.map { Tag(it.first, it.second) }),
                )
            }
        }

        return FilterList(filters)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$extLang/title/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val updatedManga = async {
            if (!fetchDetails) {
                return@async manga
            }

            val url = "$apiUrl/title/${manga.url}".toHttpUrl().newBuilder()
                .addCommonIncludeParameters()
                .build()

            client.get(url).parseAs<MangaDto>(json).data?.toSManga() ?: manga
        }
        val updatedChapters = async {
            if (!fetchChapters) {
                return@async chapters
            }

            getChapterList(manga.url)
        }

        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    private suspend fun getChapterList(mangaId: String): List<SChapter> {
        val firstPage = client.get(chapterListUrl(mangaId, 0)).parseAs<ChapterListDto>(json)

        val limit = firstPage.meta.limit
        val total = firstPage.meta.total

        val remainingPages = coroutineScope {
            (limit until total step limit).map { offset ->
                async {
                    client.get(chapterListUrl(mangaId, offset)).parseAs<ChapterListDto>(json)
                }
            }.awaitAll()
        }

        val chapters = (listOf(firstPage) + remainingPages)
            .flatMap { it.data }
            .toMutableList()

        if (chapters.isEmpty()) {
            return emptyList()
        }

        val accessibleChapterMap: Map<String, Boolean> = coroutineScope {
            chapters.map { it.id }.chunked(200).map { chapterIds ->
                async {
                    val url = "$apiUrl/gating/check"
                    val body = EntityAccessRequestDto(
                        entities = chapterIds.map { EntityAccessRequestItemDto(it, "chapter") },
                    ).toJsonRequestBody(json)
                    val response = client.post(url, body)

                    response.parseAs<EntityAccessMapDto>(json)
                        .data?.attributes?.map ?: emptyMap()
                }
            }.awaitAll().fold(mutableMapOf()) { acc, map ->
                acc.apply { putAll(map) }
            }
        }

        return chapters.mapNotNull {
            val isAccessible = accessibleChapterMap[it.id]!!
            when {
                isAccessible -> it.toSChapter()
                preferences.showLockedChapters -> {
                    it.toSChapter().apply {
                        name = "🔒 $name"
                        memo = buildJsonObject {
                            put("needs_auth", true)
                        }
                    }
                }
                else -> null
            }
        }
    }

    private fun chapterListUrl(mangaId: String, offset: Int): HttpUrl = "$apiUrl/chapter".toHttpUrl().newBuilder()
        .addQueryParameter("titleId", mangaId)
        .addQueryParameter("includes[]", "organization")
        .addQueryParameter("limit", "200")
        .addQueryParameter("offset", offset.toString())
        .addQueryParameter("translatedLanguages[]", extLang)
        .addQueryParameter("order[volume]", "desc")
        .addQueryParameter("order[chapter]", "desc")
        .build()

    private fun ChapterDataDto.toSChapter(): SChapter {
        val attr = attributes!!
        val chapterName = mutableListOf<String>()

        attr.volume?.let {
            if (it.isNotEmpty()) {
                chapterName.add("Vol.$it")
            }
        }

        attr.chapter?.let {
            if (it.isNotEmpty()) {
                chapterName.add("Ch.$it")
            }
        }

        attr.name?.let {
            if (it.isNotEmpty()) {
                if (chapterName.isNotEmpty()) {
                    chapterName.add("-")
                }
                chapterName.add(it)
            }
        }

        return SChapter.create().apply {
            url = id
            name = chapterName.joinToString(" ")
            date_upload = Instant.tryParse(attr.publishAt)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/$extLang/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val needsAuth = chapter.memo.getBooleanOrNull("needs_auth") ?: false

        val url = "$apiUrl/images/chapter/${chapter.url}?newQualities=true"

        val response = if (needsAuth) {
            client.get(url, authHeaders(), ensureSuccess = false)
        } else {
            client.get(url, ensureSuccess = false).let {
                if (it.code == 402) {
                    it.close()
                    client.get(url, authHeaders(), ensureSuccess = false)
                } else {
                    it
                }
            }
        }

        if (!response.isSuccessful) {
            throw when (response.code) {
                402 -> when {
                    token == null ->
                        Exception("Locked Chapter, login via webview to authorize")
                    else ->
                        Exception("Locked Chapter, purchase the chapter on the site")
                }
                else -> HttpException(response.code)
            }
        }

        val data = response.parseAs<PageListDto>(json).data
            ?: return emptyList()

        val hash = data.hash
        val prefix = "${data.baseUrl}/chapter/${chapter.url}/$hash"

        return if (preferences.useDataSaver) {
            data.low.mapIndexed { idx, it -> Page(idx, imageUrl = prefix + "/low/${it.filename}") }
        } else {
            data.source.mapIndexed { idx, it -> Page(idx, imageUrl = prefix + "/source/${it.filename}") }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "${COVER_QUALITY_PREF}_$extLang"
            title = "Cover quality"
            entries = arrayOf("Original", "Medium", "Low")
            entryValues = arrayOf("", ".512.jpg", ".256.jpg")
            setDefaultValue("")
            summary = "%s"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = "${DATA_SAVER_PREF}_$extLang"
            title = "Data saver"
            summary = "Enables smaller, more compressed images"
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = "${SHOW_LOCKED_CHAPTERS_PREF}_$extLang"
            title = "Show locked/paywalled chapters"
            summary = "Display chapters that require an account with a premium subscription"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun HttpUrl.Builder.addCommonIncludeParameters() = apply {
        addQueryParameter("includes[]", "cover_art")
        addQueryParameter("includes[]", "organization")
        addQueryParameter("includes[]", "tag")
        addQueryParameter("includes[]", "primary_tag")
        addQueryParameter("includes[]", "secondary_tag")
    }

    private fun HttpUrl.Builder.addCommonTypeParameters() = apply {
        addQueryParameter("types[]", "manhua")
        addQueryParameter("types[]", "manwha")
        addQueryParameter("types[]", "manga")
        addQueryParameter("types[]", "comic")
    }

    private val SharedPreferences.coverQuality
        get() = getString("${COVER_QUALITY_PREF}_$extLang", "")

    private val SharedPreferences.useDataSaver
        get() = getBoolean("${DATA_SAVER_PREF}_$extLang", false)

    private val SharedPreferences.showLockedChapters
        get() = getBoolean("${SHOW_LOCKED_CHAPTERS_PREF}_$extLang", false)

    private var token: Token? = null
    private val mutex = Mutex()

    private suspend fun login() = mutex.withLock {
        if (token == null) {
            token = getLocalStorage(baseUrl, "namicomi.user:https://auth.namicomi.com/realms/namicomi:namicomi-frontend")?.parseAs()
        }

        val current = token ?: return@withLock

        current.refreshExpires?.let {
            if (Clock.System.now() > it) {
                cleanUpToken()
                return@withLock
            }
        }

        if (Clock.System.now().plus(30.seconds) > current.expires) {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", current.refreshToken)
                .add("scope", current.scope)
                .add("client_id", "namicomi-frontend")
                .build()

            val response = client.post(
                "https://auth.$domain/realms/namicomi/protocol/openid-connect/token",
                body,
                ensureSuccess = false,
            )

            if (response.isSuccessful) {
                val refresh = response.parseAs<RefreshTokenResponse>()
                token = current.copy(
                    accessToken = refresh.accessToken,
                    refreshToken = refresh.refreshToken,
                    expiresAt = refresh.expires.epochSeconds,
                    refreshExpiresAt = refresh.refreshExpires?.epochSeconds,
                )
            } else {
                cleanUpToken()
            }
        }
    }

    private suspend fun cleanUpToken() {
        token = null
        runWebView(10.seconds) {
            onPageFinished {
                evaluateJs("""localStorage.removeItem("namicomi.user:https://auth.namicomi.com/realms/namicomi:namicomi-frontend")""")
                resolve(Unit)
            }
            loadData(baseUrl, "")
        }
    }

    private suspend fun authHeaders(): Headers {
        login()

        return headersBuilder().apply {
            token?.also {
                add("Authorization", "Bearer ${it.accessToken}")
            }
        }.build()
    }
}

const val MANGA_LIMIT = 20
private val whitespaceRegex = "\\s+".toRegex()
private const val COVER_QUALITY_PREF = "thumbnailQuality"
private const val DATA_SAVER_PREF = "dataSaver"
private const val SHOW_LOCKED_CHAPTERS_PREF = "showLockedChapters"
private const val AUTH_TOKEN = "auth_token"
