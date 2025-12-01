package eu.kanade.tachiyomi.extension.all.sakuramanhwa

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class BlossomManhwa(
    override val lang: String = "all",
) : HttpSource(), ConfigurableSource {
    override val name = "BlossomManhwa"

    override val id = 1781921631032816989

    override val supportsLatest = true

    override val baseUrl = "https://api.blossommanhwa.com"

    private val apiImageUrl = "https://api.blossommanhwa.com/v1/images"
    private val cdnImageUrl = "https://cdn.blossommanhwa.com/v1/images"

    private val secretKey = "EA^UfBOF9lNdQDS3i2qAnsqxIrTpH%"
    private val encryptKey = "6dFGd4Laa3vE%kLpr5eCtSEaAL%wJm"

    private val apiCryptoHelper = CryptoHelper(baseUrl, secretKey, encryptKey)

    override val client: OkHttpClient =
        network.cloudflareClient.newBuilder().addInterceptor(apiCryptoHelper)
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (!response.isSuccessful && request.url.toString().startsWith(baseUrl)) {
                    throw IOException(response.body.string())
                }
                response
            }.build().also { apiCryptoHelper.setClient(it) }

    private val preference = getPreferences()

    private val i18nHelper: I18nHelper = I18nHelper("https://blossommanhwa.com", client, preference)

    // Chapter

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ENGLISH)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<ApiMangaInfo>()
        val tag = when (data.manga.type) {
            "manhwa" -> "api"
            "manga" -> "cdn"
            else -> throw UnsupportedOperationException()
        }

        val lis = mutableListOf<SChapter>()
        data.chapters.forEach {
            val chapterName = getChapterName(it.number)
            lis.add(
                SChapter.create().apply {
                    url = "$tag/v1/manga/${data.manga.slug}/chapter/$chapterName"
                    name = "${chapterName}${if (it.title != null) " ${it.title}" else ""}"
                    date_upload = dateFormat.tryParse(it.create_at)
                    chapter_number = it.number
                },
            )
        }

        return lis
    }

    private fun getChapterName(number: Float): String {
        return if (number % 1 == 0f) "${number.toInt()}" else "$number"
    }

    // Image

    override fun imageRequest(page: Page): Request {
        val (tag, realUrl) = getTagUrl(page.imageUrl!!)
        val prefixUrl = when (tag) {
            "api" -> apiImageUrl
            "cdn" -> cdnImageUrl
            else -> throw UnsupportedOperationException()
        }
        return GET("$prefixUrl$realUrl", headers)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // LatestUpdates

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return focusFetchManga(page == 1, this::latestUpdatesRequest, this::latestUpdatesParse)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().encodedPath("/v1/manga/search/latesUpdates")
            .addQueryParameter("limit", "72").addQueryParameter("page", "$page").build().toString(),
        headers,
    )

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<ApiMangaInfo>()

        return mangaDetailsToSManga(data.manga).apply {
            genre = listOf(
                genre!!,
                "follows: ${data.metaData.follows}",
                "views:  ${data.metaData.views}",
            ).joinToString()
        }
    }

    private fun mangaDetailsToSManga(details: MangaDetails): SManga {
        return SManga.create().apply {
            url = "/v1/manga/findBySlug/${details.slug}"
            title = getTitle(details.title, details.language)
            genre = buildList {
                add("lang: ${details.language}")
                add("type: ${details.type}")
                details.authors?.forEach { add("author: $it") }
                details.rating?.also { add("rating: $it") }
            }.joinToString()
            status = if (details.status == "ongoing") SManga.ONGOING else SManga.COMPLETED
            thumbnail_url = "$baseUrl/v1/images/manga${details.img}"
        }
    }

    // Pages

    private fun getTagUrl(tagUrl: String): Pair<String, String> {
        return Pair(tagUrl.substring(0, 3), tagUrl.substring(3))
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val (tag, realUrl) = getTagUrl(chapter.url)

        val response = client.newCall(GET("$baseUrl$realUrl", headers)).execute()
        val data = response.parseAs<ApiChapterInfo>()

        val lis = mutableListOf<Page>()
        data.chapter.images.forEachIndexed { index, it ->
            lis.add(Page(index, imageUrl = "$tag/chapter$it"))
        }

        return Observable.just(lis)
    }

    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException()

    // Popular

    // Independent page-turn count, used to force content filtering, single request if there is no content and there is the next page to continue the request
    private var focusPage: Int = 1

    private fun focusFetchManga(
        reset: Boolean,
        reqFunc: (page: Int) -> Request,
        respFunc: (response: Response) -> MangasPage,
    ): Observable<MangasPage> {
        if (reset) {
            focusPage = 1
        }
        var hasNextPage = true
        var mangasPage: MangasPage? = null
        while (hasNextPage) {
            val request = reqFunc(focusPage)
            val response = client.newCall(request).execute()
            mangasPage = respFunc(response)
            hasNextPage = mangasPage.hasNextPage
            focusPage++
            if (mangasPage.mangas.isNotEmpty()) {
                break
            }
        }
        return Observable.just(mangasPage!!)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return focusFetchManga(page == 1, this::popularMangaRequest, this::popularMangaParse)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<ApiMangaList>()

        val focus = preference.getString(APP_FOCUS_LANGUAGE_KEY, "")!!

        val lis = mutableListOf<SManga>()
        data.mangas.forEach {
            if (focus == "" || focus == it.language) {
                lis.add(mangaDetailsToSManga(it))
            }
        }

        return MangasPage(lis, data.next_page != null)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            baseUrl.toHttpUrl().newBuilder().encodedPath("/v1/manga/views/top")
                .addQueryParameter("limit", "72").addQueryParameter("page", "$page").build()
                .toString(),
            headers,
        )
    }

    // Search

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return focusFetchManga(
            page == 1,
            { currentPage -> this.searchMangaRequest(currentPage, query, filters) },
            this::searchMangaParse,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            var groupState = 0
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is GroupFilter -> {
                        groupState = filter.setUrlPath(this)
                    }

                    is CategoryFilter -> {
                        filter.setUrlParam(this, groupState)
                    }

                    is SortFilter -> {
                        filter.setUrlParam(this, groupState)
                    }

                    is LanguageCheckBoxFilterGroup -> {
                        filter.setUrlParam(this, groupState)
                    }

                    else -> {}
                }
            }

            if (groupState == GroupTypeSearch && query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            addQueryParameter("limit", "72")
            addQueryParameter("page", page.toString())
        }.build().toString()

        return GET(url, headers)
    }

    private fun getTitle(title: String, lang: String): String {
        return capitalizeWords(title.removeSuffix(lang))
    }

    private fun capitalizeWords(str: String): String {
        return str.split(" ").joinToString(" ") {
            it.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
    }

    // Filter

    override fun getFilterList(): FilterList {
        val i18nDictionary = getI18nDictionary()
        return FilterList(
            GroupFilter(i18nDictionary),
            CategoryFilter(i18nDictionary),
            SortFilter(i18nDictionary),
            LanguageCheckBoxFilterGroup(i18nDictionary),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            title = getI18nDictionary().library.filter["language"]
            key = APP_LANGUAGE_KEY
            entries = arrayOf(
                "🇬🇧English",
                "🇪🇸Español",
                "🇨🇳中文",
                "🇷🇺Русский",
                "🇹🇷Türkçe",
                "🇮🇩Bahasa Indonesia",
                "🇹🇭ไทย",
                "🇻🇳Tiếng Việt",
            )
            entryValues = arrayOf(
                "en",
                "es",
                "zh",
                "ru",
                "tr",
                "id",
                "th",
                "vi",
            )
            setDefaultValue(entryValues[0])
            setOnPreferenceChangeListener { _, click ->
                try {
                    getI18nDictionary(click as String)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }.let { screen.addPreference(it) }

        // Lock the filter language type from the result.
        // Non-locked content is simply ignored, which makes the experience more comfortable.
        ListPreference(screen.context).apply {
            val i18nDictionary = getI18nDictionary()
            title = "👀➡️🔒"
            key = APP_FOCUS_LANGUAGE_KEY
            entries = arrayOf(
                "🔓",
                "🇬🇧${i18nDictionary.home.updates.buttons.language["english"]!!}🔒",
                "🇪🇸${i18nDictionary.home.updates.buttons.language["spanish"]!!}🔒",
                "🇨🇳${i18nDictionary.home.updates.buttons.language["chinese"]!!}🔒",
                "${i18nDictionary.home.updates.buttons.language["raw"]!!}🔒",
            )
            entryValues = arrayOf(
                "",
                "eng",
                "esp",
                "ch",
                "raw",
            )
            setDefaultValue(entryValues[0])
        }.let { screen.addPreference(it) }
    }

    private fun getI18nDictionary(language: String? = null): I18nDictionary {
        val currentLang = language ?: preference.getString(APP_LANGUAGE_KEY, "en")!!
        return runBlocking {
            withContext(Dispatchers.IO) {
                i18nHelper.getI18nByLanguage(currentLang)
            }
        }
    }
}

internal const val APP_LANGUAGE_KEY = "APP_LANGUAGE_KEY"
internal const val APP_I18N_KEY = "APP_I18N_KEY"
internal const val APP_FOCUS_LANGUAGE_KEY = "APP_FOCUS_LANGUAGE_KEY"
