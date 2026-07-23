package eu.kanade.tachiyomi.extension.en.allanime

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.GraphQLException
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class AllManga :
    KeiSource(),
    ConfigurableSource {

    private val apiDomain get() = "api.mkissa.net"

    private val apiUrl get() = "https://$apiDomain/api"

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        interceptors().removeAll { it.javaClass.simpleName == "CloudflareInterceptor" }
        rateLimit(1) { it.host == apiDomain }
    }

    override fun getHomeUrl(): String = "$baseUrl/manga"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val payload = graphQLBody(
            query = POPULAR_QUERY,
            variables = PopularVariables(
                type = "manga",
                size = LIMIT,
                dateRange = 0,
                page = page,
                allowAdult = preferences.allowAdult,
                allowUnknown = false,
            ),
        )

        client.post(apiUrl, payload).use { response ->
            val data = response.parseGraphQLAs<PopularData>()

            val mangaList = data.popular.mangas
                .mapNotNull { it.manga?.toSManga() }

            val hasNextPage = data.popular.mangas.size == LIMIT

            return MangasPage(mangaList, hasNextPage)
        }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val payload = graphQLBody(
            query = SEARCH_QUERY,
            variables = SearchVariables(
                search = SearchPayload(
                    query = query.takeUnless { it.isEmpty() },
                    sortBy = filters.firstInstanceOrNull<SortFilter>()?.getValue(),
                    genres = filters.firstInstanceOrNull<GenreFilter>()?.included,
                    excludeGenres = filters.firstInstanceOrNull<GenreFilter>()?.excluded,
                    isManga = true,
                    allowAdult = preferences.allowAdult,
                    allowUnknown = false,
                ),
                size = LIMIT,
                page = page,
                translationType = "sub",
                countryOrigin = filters.firstInstanceOrNull<CountryFilter>()?.getValue() ?: "ALL",
            ),
        )

        client.post(apiUrl, payload).use { response ->
            val data = response.parseGraphQLAs<SearchData>()

            val mangaList = data.mangas.edges
                .map(SearchManga::toSManga)

            val hasNextPage = data.mangas.edges.size == LIMIT

            return MangasPage(mangaList, hasNextPage)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            throw Exception("Unsupported url")
        }
        val id = url.pathSegments.getOrNull(1)
            ?: throw Exception("Unsupported url")

        val tmpManga = SManga.create().apply {
            this.url = id
        }

        return fetchMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override fun getMangaUrl(manga: SManga): String {
        if (manga.url.startsWith("/")) {
            val mangaId = manga.url.split("/")[2]
            return "$baseUrl/manga/$mangaId"
        } else {
            return "$baseUrl/manga/${manga.url}"
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val legacy = manga.url.startsWith("/")
        val (mangaId, mangaSlug) = if (legacy) {
            val parts = manga.url.split("/")
            parts[2] to parts[3]
        } else {
            manga.url to manga.memo["slug"]!!.string
        }

        val payload = graphQLBody(
            query = UPDATE_QUERY,
            variables = MangaUpdateVariables(mangaId, "manga@$mangaId"),
        )

        var data: MangaUpdateData? = null
        var lastError: Exception? = null
        var retryDelay = RETRY_DELAY_MS
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) delay(retryDelay)
            try {
                val result = client.post(apiUrl, payload).parseGraphQLAs<MangaUpdateData>()
                if (result.manga != null) {
                    data = result
                    break
                }
                retryDelay += RETRY_DELAY_MS
            } catch (e: GraphQLException) {
                // "Too many requests, please try again in N seconds."
                lastError = e
                val requested = retryAfterRegex.find(e.message.orEmpty())
                    ?.groupValues?.get(1)?.toLongOrNull()?.times(1000L)
                retryDelay = requested ?: (retryDelay + RETRY_DELAY_MS)
            }
        }

        val manga = data?.manga
            ?: throw (lastError ?: Exception("Unable to fetch manga details"))
        val chapterDetails = data.chapterList.associateBy { it.chapterNum.content }

        return SMangaUpdate(
            manga = manga.toSManga(),
            chapters = manga.availableChaptersDetail.sub.map { chapterNum ->
                chapterDetails[chapterNum]!!.toSChapter(mangaId, mangaSlug, legacy)
            },
        )
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val related = manga.memo["relatedMangas"]?.parseAs<List<Related>>().orEmpty()
        val genres = manga.genre?.split(", ").orEmpty().filter { it in genreList }
        val fewerGenres = genres.shuffled().take(genres.size / 2)

        if (related.isEmpty() && genres.isEmpty()) return emptyList()

        fun searchPayload(includedGenres: List<String>) = SearchPayload(
            query = null,
            sortBy = null,
            genres = includedGenres.takeUnless { it.isEmpty() },
            excludeGenres = null,
            isManga = true,
            allowAdult = preferences.allowAdult,
            allowUnknown = false,
        )

        val payload = graphQLBody(
            query = RELATED_QUERY,
            variables = RelatedVariables(
                ids = related.map { it.mangaId },
                search = searchPayload(genres),
                fewerGenresSearch = searchPayload(fewerGenres),
                size = LIMIT,
                translationType = "sub",
            ),
        )

        val data = client.post(apiUrl, payload).parseGraphQLAs<RelatedData>()

        return (data.mangasWithIds.orEmpty() + data.search?.edges.orEmpty() + data.fewerGenresSearch?.edges.orEmpty())
            .distinctBy { it.id }
            .map(SearchManga::toSManga)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.startsWith("/")) {
            // /read/$mangaId/$mangaSlug/chapter-$chapterNum-sub
            val chapterUrlParts = chapter.url.split("/")
            val mangaId = chapterUrlParts[2]
            val chapterSlug = chapterUrlParts[4]
            return "$baseUrl/manga/$mangaId/$chapterSlug"
        } else {
            val mangaId = chapter.memo["mangaId"]!!.string
            return "$baseUrl/manga/$mangaId/chapter-${chapter.url}-sub"
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.memo["mangaId"]?.string ?: throw Exception("Refresh Chapter List")
        val mangaUrl = "$baseUrl/manga/$mangaId"
        val chapterUrl = getChapterUrl(chapter).toHttpUrl().encodedPath

        val document = client.get(mangaUrl, ensureSuccess = false).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403 || response.code == 503) {
                    throw Exception("Solve captcha in WebView and retry")
                } else {
                    throw HttpException(response.code)
                }
            }

            response.asJsoup()
        }

        val payload = runWebView {
            blockImages = true
            userAgent = headers["User-Agent"]!!

            val interfaceName = (1..(10..20).random())
                .map { (('a'..'z') + ('A'..'Z')).random() }
                .joinToString("")
            val script = """
                (function () {
                    const originalParse = JSON.parse;
                    JSON.parse = new Proxy(originalParse, {
                        apply(target, thisArg, args) {
                            const result = Reflect.apply(target, thisArg, args);
                            if (result && result.chapterPages) {
                                window.$interfaceName.post(args[0]);
                            }
                            return result;
                        }
                    });

                    function triggerChapterNav() {
                        const a = document.createElement('a');
                        a.href = a.dataset.href = '$chapterUrl';
                        document.body.append(a);
                        a.click();
                    }

                   let checkAttempts = 0;
                   const maxAttempts = 300; // 15 seconds

                   function check() {
                       if (document.querySelector('[data-href]')) {
                           // trigger early when SPA nav ready
                           triggerChapterNav();
                       } else if (checkAttempts < maxAttempts) {
                           checkAttempts++;
                           setTimeout(check, 50);
                       } else {
                           // trigger anyway
                           triggerChapterNav();
                       }
                   }
                   check();
                })();
            """.trimIndent()

            jsBridge(interfaceName) {
                resolve(it)
            }

            onPageStarted {
                evaluateJs(script)
            }

            loadData(mangaUrl, document.outerHtml())
        }

        val pageListData = payload.parseAs<PageListData>().pageList
            ?: return emptyList()

        val pages = pageListData.edges.firstOrNull {
            val fullUrlAvailable = it.pictureUrls.randomOrNull()?.url?.matches(urlRegex) == true
            val serverAvailable = it.serverUrl != null

            fullUrlAvailable || serverAvailable
        }
            ?: pageListData.edges.firstOrNull()
            ?: return emptyList()

        val imageDomain = pages.serverUrl?.let { server ->
            if (server.matches(urlRegex)) {
                "${server.removeSuffix("/")}/"
            } else {
                "https://${server.removeSuffix("/")}/"
            }
        } ?: "https://ytimgf.youtube-anime.com/"

        return pages.pictureUrls.mapIndexedNotNull { index, image ->
            image.url ?: return@mapIndexedNotNull null

            val imageUrl = if (image.url.matches(urlRegex)) {
                image.url
            } else {
                imageDomain + image.url.removePrefix("/")
            }
            Page(
                index = index,
                imageUrl = imageUrl,
            )
        }
    }

    override fun imageRequest(page: Page): Request {
        val quality = preferences.imageQuality

        if (quality == IMAGE_QUALITY_PREF_DEFAULT) {
            return super.imageRequest(page)
        }

        val oldUrl = imageQualityRegex.find(page.imageUrl!!)!!.groupValues[1]
        val newUrl = "$IMAGE_CDN/$oldUrl?w=$quality"

        return GET(newUrl, headers)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = IMAGE_QUALITY_PREF
            title = "Image Quality"
            entries = arrayOf("Original", "Wp-800", "Wp-480")
            entryValues = arrayOf("original", "800", "480")
            setDefaultValue(IMAGE_QUALITY_PREF_DEFAULT)
            summary = "Warning: Wp quality servers can be slow and might not work sometimes"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_ADULT_PREF
            title = "Show Adult Content"
            setDefaultValue(SHOW_ADULT_PREF_DEFAULT)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.allowAdult
        get() = getBoolean(SHOW_ADULT_PREF, SHOW_ADULT_PREF_DEFAULT)

    private val SharedPreferences.imageQuality
        get() = getString(IMAGE_QUALITY_PREF, IMAGE_QUALITY_PREF_DEFAULT)!!
}

private const val LIMIT = 20
private const val MAX_RETRIES = 5
private const val RETRY_DELAY_MS = 1000L
private val retryAfterRegex = Regex("""again in (\d+)\s*second""")
val urlRegex = Regex("^https?://.*")
private const val IMAGE_CDN = "https://wp.youtube-anime.com"
private val imageQualityRegex = Regex("^https?://([^#]+)")

private const val SHOW_ADULT_PREF = "pref_adult"
private const val SHOW_ADULT_PREF_DEFAULT = false
private const val IMAGE_QUALITY_PREF = "pref_quality"
private const val IMAGE_QUALITY_PREF_DEFAULT = "original"
