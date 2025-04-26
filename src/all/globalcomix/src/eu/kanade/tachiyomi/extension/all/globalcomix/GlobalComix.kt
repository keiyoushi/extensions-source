package eu.kanade.tachiyomi.extension.all.globalcomix

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.all.globalcomix.dto.ChapterDataDto.Companion.createChapter
import eu.kanade.tachiyomi.extension.all.globalcomix.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.globalcomix.dto.ChaptersDto
import eu.kanade.tachiyomi.extension.all.globalcomix.dto.EntityDto
import eu.kanade.tachiyomi.extension.all.globalcomix.dto.MangaDataDto.Companion.createManga
import eu.kanade.tachiyomi.extension.all.globalcomix.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.globalcomix.dto.MangasDto
import eu.kanade.tachiyomi.extension.all.globalcomix.dto.UnknownEntity
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

abstract class GlobalComix(final override val lang: String, private val extLang: String = lang) :
    ConfigurableSource, HttpSource() {

    override val name = "GlobalComix"
    override val baseUrl = webUrl
    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        serializersModule += SerializersModule {
            polymorphic(EntityDto::class) {
                defaultDeserializer { UnknownEntity.serializer() }
            }
        }
    }

    private val intl = Intl(
        language = lang,
        baseLanguage = english,
        availableLanguages = setOf(english),
        classLoader = this::class.java.classLoader!!,
        createMessageFileName = { lang -> Intl.createDefaultMessageFileName(lang) },
    )

    final override fun headersBuilder() = super.headersBuilder().apply {
        set("Referer", "$baseUrl/")
        set("Origin", baseUrl)
        set("x-gc-client", clientId)
        set("x-gc-identmode", "cookie")
    }

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    private fun simpleQueryRequest(page: Int, orderBy: String?, query: String?): Request {
        val url = apiSearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("lang_id[]", extLang)
            .addQueryParameter("p", page.toString())

        orderBy?.let { url.addQueryParameter("sort", it) }
        query?.let { url.addQueryParameter("q", it) }

        return GET(url.build(), headers)
    }

    override fun popularMangaRequest(page: Int): Request =
        simpleQueryRequest(page, orderBy = null, query = null)

    override fun popularMangaParse(response: Response): MangasPage =
        mangaListParse(response)

    override fun latestUpdatesRequest(page: Int): Request =
        simpleQueryRequest(page, "recent", query = null)

    override fun latestUpdatesParse(response: Response): MangasPage =
        mangaListParse(response)

    private fun mangaListParse(response: Response): MangasPage {
        val isSingleItemLookup = response.request.url.toString().startsWith(apiMangaUrl)
        return if (!isSingleItemLookup) {
            // Normally, the response is a paginated list of mangas
            // The results property will be a JSON array
            response.parseAs<MangasDto>().payload!!.let { dto ->
                MangasPage(
                    dto.results.map { it -> it.createManga() },
                    dto.pagination.hasNextPage,
                )
            }
        } else {
            // However, when using the 'id:' query prefix (via the UrlActivity for example),
            // the response is a single manga and the results property will be a JSON object
            MangasPage(
                listOf(
                    response.parseAs<MangaDto>().payload!!
                        .results
                        .createManga(),
                ),
                false,
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // If the query is a slug ID, return the manga directly
        if (query.startsWith(prefixIdSearch)) {
            val mangaSlugId = query.removePrefix(prefixIdSearch)

            if (mangaSlugId.isEmpty()) {
                throw Exception(intl["invalid_manga_id"])
            }

            val url = apiMangaUrl.toHttpUrl().newBuilder()
                .addPathSegment(mangaSlugId)
                .build()

            return GET(url, headers)
        }

        return simpleQueryRequest(page, orderBy = "relevance", query)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga): String = "$webComicUrl/${titleToSlug(manga.title)}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiMangaUrl.toHttpUrl().newBuilder()
            .addPathSegment(titleToSlug(manga.title))
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<MangaDto>().payload!!
            .results
            .createManga()

    override fun chapterListRequest(manga: SManga): Request {
        val url = apiSearchUrl.toHttpUrl().newBuilder()
            .addPathSegment(manga.url) // manga.url contains the the comic id
            .addPathSegment("releases")
            .addQueryParameter("lang_id", extLang)
            .addQueryParameter("all", "true")
            .toString()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        response.parseAs<ChaptersDto>().payload!!.results.filterNot { dto ->
            dto.isPremium && !preferences.showLockedChapters
        }.map { it.createChapter() }

    override fun getChapterUrl(chapter: SChapter): String =
        "$baseUrl/read/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterKey = chapter.url
        val url = "$apiChapterUrl/$chapterKey"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterKey = response.request.url.pathSegments.last()
        val chapterWebUrl = "$webChapterUrl/$chapterKey"

        return response.parseAs<ChapterDto>()
            .payload!!
            .results
            .page_objects!!
            .map { dto -> if (preferences.useDataSaver) dto.mobile_image_url else dto.desktop_image_url }
            .mapIndexed { index, url -> Page(index, "$chapterWebUrl/$index", url) }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val dataSaverPref = SwitchPreferenceCompat(screen.context).apply {
            key = getDataSaverPreferenceKey(extLang)
            title = intl["data_saver"]
            summary = intl["data_saver_summary"]
            setDefaultValue(false)
        }

        val showLockedChaptersPref = SwitchPreferenceCompat(screen.context).apply {
            key = getShowLockedChaptersPreferenceKey(extLang)
            title = intl["show_locked_chapters"]
            summary = intl["show_locked_chapters_summary"]
            setDefaultValue(true)
        }

        screen.addPreference(dataSaverPref)
        screen.addPreference(showLockedChaptersPref)
    }

    private inline fun <reified T> Response.parseAs(): T = parseAs(json)

    private val SharedPreferences.useDataSaver
        get() = getBoolean(getDataSaverPreferenceKey(extLang), false)

    private val SharedPreferences.showLockedChapters
        get() = getBoolean(getShowLockedChaptersPreferenceKey(extLang), true)

    companion object {
        fun titleToSlug(title: String) = title.trim()
            .lowercase(Locale.US)
            .replace(titleSpecialCharactersRegex, "-")

        val titleSpecialCharactersRegex = "[^a-z0-9]+".toRegex()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
}
