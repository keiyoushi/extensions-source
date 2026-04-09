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
) : HttpSource(),
    ConfigurableSource {
    override val name = "BlossomManhwa"

    override val id = 1781921631032816989

    override val supportsLatest = true

    override val baseUrl = "https://api.cherrymanhwa.com"

    private val apiImageUrl = "https://api.cherrymanhwa.com/v1/images"
    private val siteUrl = "https://cherrymanhwa.com"

    private val secretKey = "EA^UfBOF9lNdQDS3i2qAnsqxIrTpH%"
    private val encryptKey = "6dFGd4Laa3vE%kLpr5eCtSEaAL%wJm"
    private val imageKey = "RghVx!Sf!Dw3y6O7KQcF%pg#"

    private val apiCryptoHelper = CryptoHelper(baseUrl, secretKey, encryptKey, imageKey)

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

    // Chapter

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ENGLISH)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<ApiMangaInfo>()
        val lis = mutableListOf<SChapter>()
        data.chapters.forEach {
            val chapterName = getChapterName(it.number)
            lis.add(
                SChapter.create().apply {
                    url = "/v1/manga/${data.manga.slug}/chapter/$chapterName"
                    name = "${chapterName}${if (it.title != null) " ${it.title}" else ""}"
                    date_upload = dateFormat.tryParse(it.create_at)
                    chapter_number = it.number
                },
            )
        }

        return lis
    }

    private fun getChapterName(number: Float): String = if (number % 1 == 0f) "${number.toInt()}" else "$number"

    // Image

    override fun imageRequest(page: Page) = GET("$apiImageUrl${page.imageUrl}#DECRYPT", headers)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // webview

    override fun getChapterUrl(chapter: SChapter) = "$siteUrl${chapter.url.substringAfter("/v1")}"

    override fun getMangaUrl(manga: SManga) = "$siteUrl/manga/${manga.url.substringAfterLast("/")}"

    // LatestUpdates

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = focusFetchManga(page == 1, this::latestUpdatesRequest, this::latestUpdatesParse)

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

    private fun mangaDetailsToSManga(details: MangaDetails): SManga = SManga.create().apply {
        url = "/v1/manga/findBySlug/${details.slug}"
        title = getTitle(details.title, details.language)
        description = details.description ?: ""
        genre = buildList {
            add("lang: ${details.language}")
            add("type: ${details.type}")
        }.joinToString()
        author = details.authors?.joinToString() ?: ""
        artist = author
        status = if (details.status == "ongoing") SManga.ONGOING else SManga.COMPLETED
        thumbnail_url = "$baseUrl/v1/images/manga${details.img}"
    }

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val response = client.newCall(GET("$baseUrl${chapter.url}", headers)).execute()
        val data = response.parseAs<ApiChapterInfo>()

        val lis = mutableListOf<Page>()
        data.chapter.images.maxBy { it.size }.forEachIndexed { index, path ->
            lis.add(Page(index, imageUrl = "/chapter$path"))
        }

        return Observable.just(lis)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

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

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = focusFetchManga(page == 1, this::popularMangaRequest, this::popularMangaParse)

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

    override fun popularMangaRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().encodedPath("/v1/manga/views/top")
            .addQueryParameter("limit", "72").addQueryParameter("page", "$page").build()
            .toString(),
        headers,
    )

    // Search

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = focusFetchManga(
        page == 1,
        { currentPage -> this.searchMangaRequest(currentPage, query, filters) },
        this::searchMangaParse,
    )

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/v1/manga".toHttpUrl().newBuilder().apply {
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is LanguageCheckBoxFilterGroup -> {
                        filter.setUrlParam(this)
                    }
                    is AuthorFilter -> {
                        filter.setUrlParam(this)
                    }
                    is ArtistFilter -> {
                        filter.setUrlParam(this)
                    }
                    is SortFilter -> {
                        filter.setUrlParam(this)
                    }
                    else -> {}
                }
            }
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            addQueryParameter("limit", "50")
            addQueryParameter("page", page.toString())
        }.build().toString()

        return GET(url, headers)
    }

    private fun getTitle(title: String, lang: String): String = capitalizeWords(title.removeSuffix(lang))

    private fun capitalizeWords(str: String): String = str.split(" ").joinToString(" ") {
        it.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

    // Filter

    override fun getFilterList(): FilterList = FilterList(
        AuthorFilter(),
        ArtistFilter(),
        GenreFilter(),
        LanguageCheckBoxFilterGroup(),
        SortFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Lock the filter language type from the result.
        // Non-locked content is simply ignored, which makes the experience more comfortable.
        ListPreference(screen.context).apply {
            title = "Default Search Language: "
            key = APP_FOCUS_LANGUAGE_KEY
            entries = arrayOf(
                "All",
                "🇬🇧 English",
                "🇪🇸 Spanish",
                "🇨🇳 Chinese",
                "Raw",
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
}
internal const val APP_FOCUS_LANGUAGE_KEY = "APP_FOCUS_LANGUAGE_KEY"
