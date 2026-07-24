package eu.kanade.tachiyomi.extension.all.mangaplus

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.decodeHex
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.buffer
import java.util.UUID

@Source
abstract class MangaPlus :
    KeiSource(),
    ConfigurableSource {

    private val internalLangName: String
        get() = when (lang) {
            "en" -> "eng"
            "es" -> "esp"
            "fr" -> "fra"
            "id" -> "ind"
            "pt-BR" -> "ptb"
            "ru" -> "rus"
            "th" -> "tha"
            "vi" -> "vie"
            "de" -> "deu"
            else -> throw IllegalStateException("Unsupported lang: $lang")
        }

    private val internalLangCode: Int
        get() = when (lang) {
            "en" -> LANGUAGE_ENGLISH
            "es" -> LANGUAGE_SPANISH
            "fr" -> LANGUAGE_FRENCH
            "id" -> LANGUAGE_INDONESIAN
            "pt-BR" -> LANGUAGE_PORTUGUESE_BR
            "ru" -> LANGUAGE_RUSSIAN
            "th" -> LANGUAGE_THAI
            "vi" -> LANGUAGE_VIETNAMESE
            "de" -> LANGUAGE_GERMAN
            else -> throw IllegalStateException("Unsupported lang: $lang")
        }

    private val apiUrlHost get() = API_URL.toHttpUrl().host
    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    private val session = UUID.randomUUID().toString()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::sessionTokenIntercept)
        addInterceptor(::imageIntercept)
        rateLimit(1) { it.host == apiUrlHost }
        rateLimit(2) { it.host == baseUrlHost }
    }

    private fun sessionTokenIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != apiUrlHost) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("SESSION-TOKEN", session)
                .build(),
        )
    }

    private val intl by lazy {
        Intl(
            language = lang,
            baseLanguage = "en",
            availableLanguages = setOf("en", "pt-BR", "vi"),
            classLoader = this::class.java.classLoader!!,
        )
    }

    private val preferences = getPreferences()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$API_URL/title_list/rankingV2?lang=$internalLangName&type=hottest&clang=$internalLangName")
            .parseAsProto<MangaPlusResponse>()

        val titles = result.successOrThrow().titleRankingView!!.rankedTitles
            .flatMap(RankedTitle::titles)
            .filterByLang()

        return MangasPage(titles.map(Title::toSManga), hasNextPage = false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.get("$API_URL/web/web_homeV4?lang=$internalLangName&clang=$internalLangName")
            .parseAsProto<MangaPlusResponse>()

        val titles = result.successOrThrow().webHomeView!!.groups
            .flatMap(UpdatedTitleGroup::titles)
            .mapNotNull(UpdatedTitle::title)
            .filterByLang()

        return MangasPage(titles.map(Title::toSManga), hasNextPage = false)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val typeFilter = filters.firstInstance<TypeFilter>()
        val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.slug.orEmpty()

        // allV2 returns everything while v3 returns based on type filter (serializations/completed etc.)
        val useAllTitles = query.isNotEmpty() && genreSlug.isEmpty() && typeFilter.isDefault

        val titles = if (useAllTitles) {
            client.get("$API_URL/title_list/allV2").parseAsProto<MangaPlusResponse>()
                .successOrThrow().allTitlesView!!.allTitlesGroup
                .flatMap(AllTitlesGroup::titles)
                .filterByLang()
        } else {
            client.get(allTitlesV3Url(typeFilter.type)).parseAsProto<MangaPlusResponse>()
                .successOrThrow().allTitlesViewV3!!.titles
                .filter { genreSlug.isEmpty() || it.genres.any { genre -> genre.slug == genreSlug } }
                .map(AllTitlesV3Entry::title)
                .filterByLang()
        }

        val filtered = titles.filter { title ->
            query.isEmpty() ||
                title.name.contains(query, ignoreCase = true) ||
                title.author.orEmpty().contains(query, ignoreCase = true)
        }

        return MangasPage(filtered.map(Title::toSManga), hasNextPage = false)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val result = client.get(allTitlesV3Url(TypeFilter.DEFAULT_TYPE)).parseAsProto<MangaPlusResponse>()
        return result.success?.allTitlesViewV3?.tags.orEmpty().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<TagName>>().orEmpty()
        return FilterList(
            buildList {
                add(TypeFilter())
                if (genres.isNotEmpty()) {
                    add(GenreFilter(genres))
                }
            },
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host !in MANGAPLUS_HOSTS) return null

        val titleId = when {
            url.pathSegments.firstOrNull() == "titles" -> url.pathSegments.getOrNull(1)
            url.pathSegments.firstOrNull() == "viewer" ->
                url.pathSegments.getOrNull(1)?.let { titleIdFromChapter(it) }
            url.pathSegments.lastOrNull() == "sns_share" -> url.queryParameter("title_id")
            else -> null
        } ?: return null

        val titleDetail = getTitleDetails(titleId)
            .takeIf { it.title.language == internalLangCode }
            ?: return null

        return titleDetail.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val titleDetail = getTitleDetails(manga.url.substringAfterLast("/"))
            .takeIf { it.title.language == internalLangCode }
            ?: throw Exception(intl["not_available"])

        val subtitleOnly = preferences.subtitleOnly()
        val chapterList = titleDetail.chapterList
            .filterNot(Chapter::isExpired)
            .map { it.toSChapter(subtitleOnly) }
            .reversed()

        return SMangaUpdate(titleDetail.toSManga(), chapterList)
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val genres = manga.genre?.split(", ")?.filter(String::isNotEmpty)?.toSet().orEmpty()
        if (genres.isEmpty()) return emptyList()

        val titleId = manga.url.substringAfterLast("/")
        val result = client.get(allTitlesV3Url(TypeFilter.DEFAULT_TYPE)).parseAsProto<MangaPlusResponse>()

        return result.success?.allTitlesViewV3?.titles.orEmpty()
            .filter { entry -> entry.genres.any { it.name in genres } }
            .map(AllTitlesV3Entry::title)
            .filterByLang()
            .filter { it.titleId.toString() != titleId }
            .map(Title::toSManga)
    }

    // Remove the '#' and map to the url format used on the website.
    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url.substring(1)

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substring(1)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("/")
        val result = client.get(mangaViewerUrl(chapterId)).parseAsProto<MangaPlusResponse>()

        val viewer = result.successOrThrow { error ->
            when {
                error?.subject == NOT_FOUND_SUBJECT -> intl["chapter_expired"]
                !error?.body.isNullOrEmpty() -> error.body
                else -> intl["unknown_error"]
            }
        }.mangaViewer!!

        val viewToken = viewer.viewToken.orEmpty()

        return viewer.pages
            .mapNotNull(MangaPlusPage::mangaPage)
            .mapIndexed { i, page ->
                val encryptionKey = page.encryptionKey?.let { "#$it" }.orEmpty()
                Page(i, url = viewToken, imageUrl = page.imageUrl + encryptionKey)
            }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Plus-Vw-Token", page.url)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    private suspend fun getTitleDetails(titleId: String): TitleDetailView {
        val result = client.get("$API_URL/title_detailV3?title_id=$titleId&clang=$internalLangName")
            .parseAsProto<MangaPlusResponse>()

        return result.successOrThrow { error ->
            when {
                error?.subject == NOT_FOUND_SUBJECT -> intl["title_removed"]
                !error?.body.isNullOrEmpty() -> error.body
                else -> intl["unknown_error"]
            }
        }.titleDetailView!!
    }

    private suspend fun titleIdFromChapter(chapterId: String): String? {
        val result = client.get(mangaViewerUrl(chapterId)).parseAsProto<MangaPlusResponse>()
        return result.success?.mangaViewer?.titleId?.toString()
    }

    private fun mangaViewerUrl(chapterId: String): HttpUrl = "$API_URL/manga_viewer_v3".toHttpUrl().newBuilder()
        .addQueryParameter("chapter_id", chapterId)
        .addQueryParameter("split", if (preferences.splitImages()) "yes" else "no")
        .addQueryParameter("img_quality", preferences.imageQuality())
        .addQueryParameter("clang", internalLangName)
        .build()

    private fun allTitlesV3Url(type: String): String = "$API_URL/title_list/all_v3?type=$type&lang=$internalLangName&clang=$internalLangName"

    private fun List<Title>.filterByLang(): List<Title> = filter { it.titleId != 0 && it.language == internalLangCode }.distinctBy(Title::titleId)

    private fun MangaPlusResponse.successOrThrow(
        errorMessage: (Popup?) -> String = { it?.body?.takeIf(String::isNotEmpty) ?: intl["unknown_error"] },
    ): SuccessResult = success ?: throw Exception(errorMessage(error?.langPopup(internalLangCode)))

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val encryptionKey = request.url.fragment

        if (encryptionKey.isNullOrEmpty()) {
            return response
        }

        val keyStream = encryptionKey.decodeHex()
        val contentType = (response.headers["Content-Type"] ?: "image/jpeg").toMediaTypeOrNull()
        val body = response.body.source()

        val decrypted = object : okio.Source by body {
            private var index = 0
            private val buffer = Buffer()

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = body.read(buffer, byteCount)
                if (read == -1L) return -1L

                val bytes = buffer.readByteArray()
                for (i in bytes.indices) {
                    bytes[i] = (bytes[i].toInt() xor (keyStream[index++ % keyStream.size].toInt() and 0xFF)).toByte()
                }
                sink.write(bytes)
                return read
            }
        }

        return response.newBuilder()
            .body(
                decrypted.buffer()
                    .asResponseBody(contentType, response.body.contentLength()),
            )
            .build()
    }

    // ============================= Preferences ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "${QUALITY_PREF_KEY}_$lang"
            title = intl["image_quality"]
            entries = arrayOf(
                intl["image_quality_low"],
                intl["image_quality_medium"],
                intl["image_quality_high"],
            )
            entryValues = QUALITY_PREF_ENTRY_VALUES
            setDefaultValue(QUALITY_PREF_DEFAULT_VALUE)
            summary = "%s"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = "${SPLIT_PREF_KEY}_$lang"
            title = intl["split_double_pages"]
            summary = intl["split_double_pages_summary"]
            setDefaultValue(SPLIT_PREF_DEFAULT_VALUE)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = "${SUBTITLE_ONLY_KEY}_$lang"
            title = intl["subtitle_only"]
            summary = intl["subtitle_only_summary"]
            setDefaultValue(SUBTITLE_ONLY_DEFAULT_VALUE)
        }.also(screen::addPreference)
    }

    private fun SharedPreferences.imageQuality(): String = getString("${QUALITY_PREF_KEY}_$lang", QUALITY_PREF_DEFAULT_VALUE)!!

    private fun SharedPreferences.splitImages(): Boolean = getBoolean("${SPLIT_PREF_KEY}_$lang", SPLIT_PREF_DEFAULT_VALUE)

    private fun SharedPreferences.subtitleOnly(): Boolean = getBoolean("${SUBTITLE_ONLY_KEY}_$lang", SUBTITLE_ONLY_DEFAULT_VALUE)
}

private const val API_URL = "https://jumpg-webapi.tokyo-cdn.com/api"

private val MANGAPLUS_HOSTS = listOf(
    "mangaplus.shueisha.co.jp",
    "www.mangaplus.shueisha.co.jp",
    "jumpg-webapi.tokyo-cdn.com",
    "www.jumpg-webapi.tokyo-cdn.com",
)

private const val QUALITY_PREF_KEY = "imageResolution"
private val QUALITY_PREF_ENTRY_VALUES = arrayOf("low", "high", "super_high")
private val QUALITY_PREF_DEFAULT_VALUE = QUALITY_PREF_ENTRY_VALUES[2]

private const val SPLIT_PREF_KEY = "splitImage"
private const val SPLIT_PREF_DEFAULT_VALUE = true

private const val SUBTITLE_ONLY_KEY = "subtitleOnly"
private const val SUBTITLE_ONLY_DEFAULT_VALUE = false

private const val NOT_FOUND_SUBJECT = "Not Found"
