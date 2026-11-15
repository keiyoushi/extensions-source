package eu.kanade.tachiyomi.extension.zh.kaobeimanhua

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.asJsoup
import keiyoushi.utils.decodeHex
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Kaobeimanhua : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == API_HOST }

    override suspend fun getPopularManga(page: Int): MangasPage = getComics(page, "-popular", theme = null)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getComics(page, "-datetime_updated", theme = null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            val ordering = filters.firstInstanceOrNull<SortFilter>()?.ordering ?: "-datetime_updated"
            return getComics(page, ordering, filters.firstInstanceOrNull<ThemeFilter>()?.selected)
        }
        return api<ComicListDto>(
            "search/comic",
            "platform" to "1",
            "q" to query.trim(),
            "q_type" to "",
            "limit" to PAGE_SIZE.toString(),
            "offset" to ((page - 1) * PAGE_SIZE).toString(),
        ).toMangasPage()
    }

    private suspend fun getComics(page: Int, ordering: String, theme: String?): MangasPage = api<ComicListDto>(
        "comics",
        "ordering" to ordering,
        "theme" to theme,
        "limit" to PAGE_SIZE.toString(),
        "offset" to ((page - 1) * PAGE_SIZE).toString(),
    ).toMangasPage()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") !in DOMAINS) return null
        val segments = url.pathSegments
        val pathWord = when {
            segments[0] == "comic" -> segments.getOrNull(1)
            segments[0] != "h5" -> null
            segments.getOrNull(1) == "details" -> segments.getOrNull(3)
            segments.getOrNull(1) == "comicContent" -> segments.getOrNull(2)
            else -> null
        }?.takeIf { it.isNotEmpty() } ?: return null
        return getComic(pathWord).comic.toSMangaDetails().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // The chapter lists are per group, and only the details give the groups
        val comic = getComic(manga.url)
        val chapterList = if (fetchChapters) comic.groups.values.flatMap { getChapters(manga.url, it) } else chapters
        return SMangaUpdate(comic.comic.toSMangaDetails(), chapterList)
    }

    private suspend fun getComic(pathWord: String): ComicDetailsDto = api("comic2/$pathWord", "platform" to "1")

    private suspend fun getChapters(pathWord: String, group: GroupDto): List<SChapter> {
        val chapters = mutableListOf<ChapterDto>()
        do {
            val page = api<ChapterListDto>(
                "comic/$pathWord/group/${group.pathWord}/chapters",
                "limit" to CHAPTER_PAGE_SIZE.toString(),
                "offset" to chapters.size.toString(),
            )
            chapters += page.list
        } while (chapters.size < page.total && page.list.isNotEmpty())
        return chapters.asReversed().map { it.toSChapter(group) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (pathWord, uuid) = chapter.url.split('/')
        return "$baseUrl/comic/$pathWord/chapter/$uuid"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // The API and the mobile site only give web readers the first few pages: the desktop page has them all, encrypted
        val script = client.get(getChapterUrl(chapter), desktopHeaders, Fetch.DOCUMENT).asJsoup()
            .selectFirst("script:containsData(contentKey)")!!.data()
        val key = KEY_REGEX.find(script)!!.groupValues[1]
        val contentKey = CONTENT_KEY_REGEX.find(script)!!.groupValues[1]
        if (contentKey.isEmpty()) return emptyList()
        val cipher = aesCbc(key.toByteArray(), contentKey.take(16).toByteArray())
        return contentKey.drop(16).decodeHex().aesDecrypt(cipher).decodeToString().parseAs<List<ImageDto>>()
            .mapIndexed { index, image -> Page(index, imageUrl = image.url) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("輸入關鍵字搜尋時，篩選不會生效"),
        SortFilter(),
        ThemeFilter(),
    )

    // Without the headers of the site's mobile pages, the API answers with empty lists
    private val apiHeaders: Headers get() = headersBuilder()
        .set("platform", "1")
        .set("version", WEB_VERSION)
        .build()

    private val desktopHeaders: Headers get() = headersBuilder()
        .apply { get("User-Agent")?.let { set("User-Agent", it.toDesktopUserAgent()) } }
        .build()

    private suspend inline fun <reified T> api(path: String, vararg query: Pair<String, String?>): T {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply { query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) } }
            .build()
        val response = client.get(url, apiHeaders, Fetch.XHR).parseAs<ResponseDto>()
        if (response.code != 200) throw Exception(response.message)
        return response.results.parseAs()
    }

    companion object {
        private const val API_HOST = "api.copy4000.com"
        private const val API_URL = "https://$API_HOST/api/v3"
        private const val WEB_VERSION = "2026.10.16"
        private const val PAGE_SIZE = 30
        private const val CHAPTER_PAGE_SIZE = 500
        private val DOMAINS = setOf("mangacopy.com", "copy4000.com", "copy5000.com", "copy20.com", "copymanga.fun")
        private val KEY_REGEX = Regex("""var cct = '([^']*)'""")
        private val CONTENT_KEY_REGEX = Regex("""var contentKey = '([^']*)'""")
    }
}

// The app's mobile Chrome user agent, as the same Chrome on a Windows desktop
private fun String.toDesktopUserAgent() = replace(ANDROID_PLATFORM_REGEX, "(Windows NT 10.0; Win64; x64)").replace(" Mobile Safari/", " Safari/")

private val ANDROID_PLATFORM_REGEX = Regex("""\(Linux; Android[^)]*\)""")
