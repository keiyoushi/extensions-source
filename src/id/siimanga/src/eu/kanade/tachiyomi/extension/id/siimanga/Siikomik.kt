package eu.kanade.tachiyomi.extension.id.siimanga

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
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

@Source
abstract class Siikomik :
    KeiSource(),
    ConfigurableSource {

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    private val showLocked get() = preferences.getBoolean(PREF_SHOW_LOCKED, false)

    private val apiUrl get() = "$baseUrl/api/v1".toHttpUrl()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::resignExpiredImage)

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("popular")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("latest")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrl.newBuilder().apply {
            addPathSegment("comic")
            addQueryParameter("limit", "50")
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<SelectFilter>().forEach { filter ->
                filter.value.takeIf(String::isNotEmpty)?.let { addQueryParameter(filter.param, it) }
            }
            filters.firstInstanceOrNull<GenreFilter>()?.selected?.forEach { addQueryParameter("genre", it) }
        }.build()

        val data = client.get(url, Fetch.XHR).parseAs<ComicListDto>()
        return MangasPage(data.items.map { it.toSManga() }, data.nextCursor != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != OLD_HOST) return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size !in 2..3 || segments[0] != "komik") return null
        val manga = SManga.create().apply { this.url = mangaPath(segments[1]) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val path = manga.path
        val comicUrl = comicUrl(path.slug())
        val page = getMangaUrl(manga)
        val detailsDeferred = if (fetchDetails) async { client.get(comicUrl, Fetch.XHR.from(page)).parseAs<ComicDto>() } else null
        val chaptersDeferred = if (fetchChapters) {
            async {
                client.get(comicUrl.newBuilder().addPathSegment("chapters").build(), Fetch.XHR.from(page))
                    .parseAs<ChapterListDto>().items
                    .filter { showLocked || !it.isLocked }
                    .map { it.toSChapter(path) }
            }
        } else {
            null
        }
        SMangaUpdate(detailsDeferred?.await()?.applyTo(manga, baseUrl) ?: manga, chaptersDeferred?.await() ?: chapters)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().resolve(manga.path)!!.toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.mangaPath)!!.newBuilder()
        .addPathSegment(chapter.url)
        .addPathSegment("")
        .build()
        .toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.mangaPath.slug()
        val response = client.get(pagesUrl(slug, chapter.url), Fetch.XHR.from(getChapterUrl(chapter)), ensureSuccess = false)
        if (!response.isSuccessful) {
            val locked = response.code == 403 && response.body.contentType()?.subtype == "json" && response.parseAs<ErrorDto>().code == "CHAPTER_LOCKED"
            response.close()
            if (locked) throw Exception("Log in and unlock this chapter in WebView")
            throw HttpException(response.code)
        }
        return response.parseAs<PagesDto>().pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.url.toHttpUrl().newBuilder().fragment("$slug/${chapter.url}").build().toString())
        }
    }

    private fun comicUrl(slug: String) = apiUrl.newBuilder().addPathSegment("comic").addPathSegment(slug).build()

    private fun pagesUrl(slug: String, chapter: String) = comicUrl(slug).newBuilder().addPathSegment(chapter).addPathSegment("pages").build()

    // Image links are signed until a set time; the fragment holds "<comic>/<chapter>" for pages, "<comic>" for covers
    private fun resignExpiredImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.fragment ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (response.code != 403) return response
        response.close()

        val url = resignedUrl(key, request.url) ?: throw IOException("Image not found for $key")
        return chain.proceed(request.newBuilder().url(url).build())
    }

    // Last re-signed chapter, so its other expired pages need no further request
    private var resigned: ResignedPages? = null

    @Synchronized
    private fun resignedUrl(key: String, expired: HttpUrl): HttpUrl? = try {
        val slug = key.substringBefore('/')
        val chapter = key.substringAfter('/', "")
        if (chapter.isEmpty()) {
            // A replaced cover gets a new file name, so take the comic's current one
            apiCall(comicUrl(slug)).parseAs<ComicDto>().coverUrl?.toHttpUrl()
        } else {
            val cached = resigned?.takeIf { it.key == key }
                ?.urls?.firstOrNull { it.encodedPath == expired.encodedPath }
                ?.takeIf { it.query != expired.query && it.isSigned() }
            cached ?: run {
                val urls = apiCall(pagesUrl(slug, chapter)).parseAs<PagesDto>().pages.map { it.url.toHttpUrl() }
                resigned = ResignedPages(key, urls)
                urls.firstOrNull { it.encodedPath == expired.encodedPath }
            }
        }
    } catch (e: Exception) {
        throw e as? IOException ?: IOException(e)
    }

    private fun apiCall(url: HttpUrl): Response {
        val response = client.newCall(GET(url, headers).newBuilder().fetch(Fetch.XHR).build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        return response
    }

    private class ResignedPages(val key: String, val urls: List<HttpUrl>)

    private fun HttpUrl.isSigned() = queryParameter("exp")?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) > Clock.System.now() } == true

    override suspend fun fetchFilterData(): JsonElement = client.get(apiUrl.newBuilder().addPathSegment("terms").build(), Fetch.XHR)
        .parseAs<TermsDto>().items
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter())
            add(StatusFilter())
            add(TypeFilter())
            data?.parseAs<List<GenreDto>>()?.let { add(GenreFilter(it)) }
        },
    )

    // Older entries keep their path in memo, with the url holding a post id
    private val SManga.path get() = memo[MEMO_PATH]?.stringOrNull ?: url

    private val SChapter.mangaPath get() = memo[MEMO_MANGA_PATH]?.stringOrNull ?: throw Exception("Refresh the chapter list")

    private fun String.slug() = baseUrl.toHttpUrl().resolve(this)!!.pathSegments.filter(String::isNotEmpty).last()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "Show locked chapters"
            summary = "Coin chapters, readable after logging in and unlocking them on the site in WebView"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val OLD_HOST = "siikomik.id"
        private const val PREF_SHOW_LOCKED = "pref_show_locked_chapters"
    }
}
