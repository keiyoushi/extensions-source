package eu.kanade.tachiyomi.extension.en.dynasty

import android.util.LruCache
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.use

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
        val entries = data.chapters.flatMap { chapter ->
            chapter.getMangasFromChapter()
        }.distinct()
            .map(MangaEntry::toSManga)

        return MangasPage(
            mangas = entries,
            hasNextPage = data.hasNextPage(),
        )
    }

    private fun BrowseChapter.getMangasFromChapter(): List<MangaEntry> {
        val entries = mutableListOf<MangaEntry>()
        var isSeries = false

        tags.forEach { tag ->
            if (tag.directory in listOf("series", "anthologies", "doujins", "issues")) {
                MangaEntry(
                    url = "/${tag.directory!!}/${tag.permalink}",
                    title = tag.name,
                    cover = getCoverUrl(tag.directory, tag.permalink),
                ).also(entries::add)

                // true if an associated series is found
                isSeries = isSeries || tag.directory == "series"
            }
        }

        // individual chapter if no linked series
        // mostly the case for uploaded doujins
        if (!isSeries) {
            MangaEntry(
                url = "/chapters/$permalink",
                title = title,
                cover = buildChapterCoverFetchUrl(permalink),
            ).also(entries::add)
        }

        return entries
    }

    // lazy because extension inspector doesn't have implementation
    private val lruCache by lazy { LruCache<String, Int>(10) }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val authors = run {
            val authorQueries = filters.firstInstance<AuthorFilter>().values

            authorQueries.map { author ->
                lruCache[author]
                    ?: fetchTagId(author, "Author")
                        ?.also { lruCache.put(author, it) }
                    ?: throw Exception("Unknown Author: $author")
            }
        }
        val scanlators = run {
            val scanlatorQueries = filters.firstInstance<ScanlatorFilter>().values

            scanlatorQueries.map { scanlator ->
                lruCache[scanlator]
                    ?: fetchTagId(scanlator, "Scanlator")
                        ?.also { lruCache.put(scanlator, it) }
                    ?: throw Exception("Unknown Scanlator: $scanlator")
            }
        }

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
            authors.forEach { author ->
                addQueryParameter("with[]", author.toString())
            }
            scanlators.forEach { scanlator ->
                addQueryParameter("with[]", scanlator.toString())
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    private fun fetchTagId(query: String, type: String): Int? {
        val url = "$baseUrl/tags/suggest"
        val body = FormBody.Builder()
            .add("query", query)
            .build()

        val data = client.newCall(POST(url, headers, body)).execute()
            .parseAs<List<TagSuggest>>()

        return data.firstOrNull {
            it.type == type && it.name.almostEquals(query, 10f)
        }?.id
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
            AuthorFilter(),
            ScanlatorFilter(),
            Filter.Header("Author and Scanlator filter require almost exact name"),
            Filter.Header("Add multiple by comma (,) separation"),
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

            MangaEntry(
                url = "/$directory/$permalink",
                title = title,
                cover = getCoverUrl(directory, permalink),
            ).also(entries::add)
        }

        val hasNextPage = document.selectFirst("div.pagination > ul > li.active + li > a") != null

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = hasNextPage,
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

//    private fun buildCoverUrl(file: String): String {
//        return HttpUrl.Builder().apply {
//            scheme("https")
//            host("x.0ms.dev")
//            addPathSegment("q70")
//            addEncodedPathSegments(baseUrl)
//            addEncodedPathSegments(
//                file.removePrefix("/")
//                    .substringBefore("?"),
//            )
//        }.build().toString()
//    }

    private fun buildCoverUrl(file: String): String {
        return baseUrl.toHttpUrl()
            .newBuilder()
            .addEncodedPathSegments(
                file.removePrefix("/")
                    .substringBefore("?"),
            ).build()
            .toString()
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
                if (char == '_') {
                    ' '
                } else if (capitalize) {
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
private val CHAPTER_SLUG_REGEX = Regex("""(.*?)_(ch[0-9_]+|volume_[0-9_\w]+)""")
