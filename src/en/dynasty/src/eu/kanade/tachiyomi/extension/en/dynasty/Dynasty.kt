package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.use
import rx.Observable

class Dynasty : HttpSource() {

    override val name = "Dynasty"

    override val lang = "en"

    override val baseUrl = "https://dynasty-scans.com"

    override val supportsLatest = false

    // Dynasty-Series
    override val id = 669095474988166464

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::fetchCover)
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/chapters/added.json?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<BrowseResponse>()
        val entries = LinkedHashSet<MangaEntry>()

        data.chapters.forEach { buildMangaListFromChapter(it, entries::add) }

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = data.hasNextPage(),
        )
    }

    private fun buildMangaListFromChapter(chap: BrowseChapter, add: (MangaEntry) -> Unit) {
        val parent = chap.tags.firstOrNull { it.type == "Series" }
            ?: chap.tags.firstOrNull { it.type == "Doujin" }
            ?: chap.tags.firstOrNull { it.type == "Anthology" }
            ?: chap.tags.firstOrNull { it.type == "Issue" }

        // add parent entry when chapter parent is an associated series/doujin/anthology/issue
        if (parent != null) {
            add(
                MangaEntry(
                    url = "/${parent.directory!!}/${parent.permalink}",
                    title = parent.name,
                    cover = getCoverUrl(parent.directory, parent.permalink),
                ),
            )
        }

        // add individual chapter if it doesn't have associated series
        if (parent?.type != "Series") {
            add(
                MangaEntry(
                    url = "/chapters/${chap.permalink}",
                    title = chap.title,
                    cover = buildChapterCoverFetchUrl(chap.permalink),
                ),
            )
        }
    }

    private var _authorScanlatorCache: String? = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val empty = query.isBlank() && filters.firstInstance<GenreFilter>().isEmpty()

        if (!empty) {
            return super.fetchSearchManga(page, query, filters)
        } else {
            val (type, search) = run {
                val author = filters.firstInstance<AuthorFilter>()
                val scanlator = filters.firstInstance<ScanlatorFilter>()

                if (author.state.isNotBlank()) {
                    "Author" to author.state.trim()
                } else if (scanlator.state.isNotBlank()) {
                    "Scanlator" to scanlator.state.trim()
                } else {
                    return super.fetchSearchManga(page, query, filters)
                }
            }

            // don't fetch it again if it is cached
            if (page > 1 && _authorScanlatorCache != null) {
                return fetchAuthorOrScanlator(
                    _authorScanlatorCache!!,
                    page,
                )
            }

            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", search)
                .addQueryParameter("classes[]", type)
                .build()

            val document = client.newCall(GET(url, headers)).execute().asJsoup()

            val result = document.selectFirst(".chapter-list a.name")
                ?: throw Exception("$type: $search not found")

            _authorScanlatorCache = result.absUrl("href")

            return fetchAuthorOrScanlator(
                result.absUrl("href"),
                page,
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.trim())
            filters.firstInstance<SortFilter>().also {
                addQueryParameter("sort", it.sort)
            }
            filters.firstInstance<TypeFilter>().also {
                it.checked.forEach { type ->
                    addQueryParameter("classes[]", type)
                }
            }
            filters.firstInstance<GenreFilter>().also {
                it.included.forEach { with ->
                    addQueryParameter("with[]", with.toString())
                }
                it.excluded.forEach { without ->
                    addQueryParameter("without[]", without.toString())
                }
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList(): FilterList {
        val tags = this::class.java
            .getResourceAsStream("/assets/tags.json")!!
            .bufferedReader().use { it.readText() }
            .parseAs<List<GenreTag>>()

        return FilterList(
            SortFilter(),
            TypeFilter(),
            GenreFilter(tags),
            Filter.Separator(),
            Filter.Header("Author and Scanlator filter doesn't work with Tag filter or text search; also only works one at a time"),
            AuthorFilter(),
            ScanlatorFilter(),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = LinkedHashSet<MangaEntry>()

        document.select(".chapter-list a.name").forEach { element ->
            var (directory, permalink) = element.absUrl("href")
                .toHttpUrl().pathSegments
                .let { it[0] to it[1] }
            var title = element.ownText()

            if (directory == "chapters") {
                val seriesPermalink = CHAPTER_SLUG_REGEX.find(permalink)?.groupValues?.get(1)

                if (seriesPermalink != null) {
                    directory = "series"
                    permalink = seriesPermalink
                    title = seriesPermalink.permalinkToTitle()
                }
            }

            entries.add(
                MangaEntry(
                    url = "/$directory/$permalink",
                    title = title,
                    cover = getCoverUrl(directory, permalink),
                ),
            )
        }

        val hasNextPage = document.selectFirst("div.pagination > ul > li.active + li > a") != null

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = hasNextPage,
        )
    }

    private fun fetchAuthorOrScanlator(url: String, page: Int): Observable<MangasPage> {
        return client.newCall(GET("$url.json?page=$page", headers))
            .asObservableSuccess()
            .map { response ->
                if (url.contains("/authors/")) {
                    parseAuthorList(response)
                } else {
                    parseScanlatorList(response)
                }
            }
    }

    private fun parseAuthorList(response: Response): MangasPage {
        val data = response.parseAs<AuthorResponse>()
        val entries = LinkedHashSet<MangaEntry>()

        data.taggables.forEach {
            it.directory ?: return@forEach

            entries.add(
                MangaEntry(
                    url = "/${it.directory}/${it.permalink}",
                    title = it.name,
                    cover = it.cover?.let { cover -> buildCoverUrl(cover) },
                ),
            )
        }

        data.taggings.forEach { buildMangaListFromChapter(it, entries::add) }

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = false,
        )
    }

    private fun parseScanlatorList(response: Response): MangasPage {
        val data = response.parseAs<ScanlatorResponse>()
        val entries = LinkedHashSet<MangaEntry>()

        data.taggings.forEach { buildMangaListFromChapter(it, entries::add) }

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = data.hasNextPage(),
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        throw Exception("Not yet implemented")
    }

    override fun chapterListRequest(manga: SManga): Request {
        throw Exception("Not yet implemented")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw Exception("Not yet implemented")
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        throw Exception("Not yet implemented")
    }

    override fun pageListParse(response: Response): List<Page> {
        throw Exception("Not yet implemented")
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    private val covers: Map<String, Map<String, String>> by lazy {
        this::class.java
            .getResourceAsStream("/assets/covers.json")!!
            .bufferedReader().use { it.readText() }
            .parseAs()
    }

    private fun getCoverUrl(directory: String?, permalink: String): String? {
        directory ?: return null

        if (directory == "chapters") {
            return buildChapterCoverFetchUrl(permalink)
        }

        val file = covers[directory]?.get(permalink)
            ?: return null

        return buildCoverUrl(file)
    }

    private fun buildCoverUrl(file: String): String {
        return HttpUrl.Builder().apply {
            scheme("https")
            host("x.0ms.dev")
            addPathSegment("q70")
            addEncodedPathSegments(baseUrl)
            addEncodedPathSegments(
                file.removePrefix("/")
                    .substringBefore("?"),
            )
        }.build().toString()
    }

    private fun buildChapterCoverFetchUrl(permalink: String): String {
        return HttpUrl.Builder().apply {
            scheme("http")
            host(COVER_FETCH_HOST)
            addQueryParameter("permalink", permalink)
        }.build().toString()
    }

    private fun fetchCover(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host != COVER_FETCH_HOST) {
            return chain.proceed(request)
        }

        val permalink = request.url.queryParameter("permalink")!!

        val chapterUrl = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("chapters")
            addPathSegments("$permalink.json")
        }.build()

        val page = client.newCall(GET(chapterUrl, headers)).execute()
            .parseAs<ChapterResponse>()
            .pages.first()

        val url = buildCoverUrl(page.url)

        val newRequest = request.newBuilder()
            .url(url)
            .build()

        return chain.proceed(newRequest)
    }

    private fun String.permalinkToTitle(): String {
        val result = StringBuilder(length)
        var capitalize = true
        for (char in this) {
            result.append(
                if (capitalize) {
                    char.uppercase()
                } else {
                    char.lowercase()
                },
            )

            capitalize = char == '_'
        }

        return result.toString()
    }
}

private const val COVER_FETCH_HOST = "keiyoushi-chapter-cover"
private val CHAPTER_SLUG_REGEX = Regex("""(.*?)_ch[0-9_]+""")
