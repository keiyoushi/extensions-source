package eu.kanade.tachiyomi.extension.en.dynasty

import android.util.LruCache
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
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
import org.jsoup.Jsoup
import rx.Observable

class Dynasty : HttpSource() {

    override val name = "Dynasty"

    override val lang = "en"

    override val baseUrl = "https://dynasty-scans.com"

    override val supportsLatest = false

    // Dynasty-Series
    override val id = 669095474988166464

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::fetchCoverUrlInterceptor)
        .addInterceptor(::coverInterceptor)
        .rateLimit(1, 2)
        .build()

    private val coverClient = network.cloudflareClient

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

        val chapterAuthors = LinkedHashSet<String>()
        val chapterTags = LinkedHashSet<String>()
        val chapterOthers = LinkedHashSet<Pair<String, String>>()

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

            when (tag.type) {
                "Author" -> chapterAuthors.add(tag.name)
                "General" -> chapterTags.add(tag.name)
                else -> chapterOthers.add(tag.type to tag.name)
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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val genreFilter = filters.firstInstance<GenreFilter>()
        val authorFilter = filters.firstInstance<AuthorFilter>()
        val scanlatorFilter = filters.firstInstance<ScanlatorFilter>()

        if (query.startsWith("deeplink:")) {
            var (_, directory, permalink) = query.split(":", limit = 3)

            if (directory == "chapters") {
                val seriesPermalink = CHAPTER_SLUG_REGEX.find(permalink)?.groupValues?.get(1)

                if (seriesPermalink != null) {
                    directory = "series"
                    permalink = seriesPermalink
                }
            }

            val entry = MangaEntry(
                url = "/$directory/$permalink",
                title = permalink.permalinkToTitle(),
                cover = getCoverUrl(directory, permalink),
            ).toSManga()

            return Observable.just(
                MangasPage(
                    mangas = listOf(entry),
                    hasNextPage = false,
                ),
            )
        } else if (query.isBlank()) {
            when {
                // only one genre/tag included
                genreFilter.included.size == 1 &&
                    genreFilter.excluded.isEmpty() &&
                    authorFilter.values.isEmpty() &&
                    scanlatorFilter.values.isEmpty()
                -> return fetchSingleGenre(genreFilter.included.first().permalink, page)

                // only one author specified
                genreFilter.isEmpty() &&
                    authorFilter.values.size == 1 &&
                    scanlatorFilter.values.isEmpty()
                -> return fetchSingleAuthor(authorFilter.values.first())

                // only one scanlator specified
                genreFilter.isEmpty() &&
                    authorFilter.values.isEmpty() &&
                    scanlatorFilter.values.size == 1
                -> return fetchSingleScanlator(scanlatorFilter.values.first(), page)
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    // lazy because extension inspector doesn't have implementation
    private val lruCache by lazy { LruCache<String, Int>(10) }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val authors = filters.firstInstance<AuthorFilter>().values.map { author ->
            lruCache[author]
                ?: fetchTagId(author, "Author")
                    ?.also { lruCache.put(author, it) }
                ?: throw Exception("Unknown Author: $author")
        }
        val scanlators = filters.firstInstance<ScanlatorFilter>().values.map { scanlator ->
            lruCache[scanlator]
                ?: fetchTagId(scanlator, "Scanlator")
                    ?.also { lruCache.put(scanlator, it) }
                ?: throw Exception("Unknown Scanlator: $scanlator")
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
                    addQueryParameter("with[]", with.id.toString())
                }
                it.excluded.forEach { without ->
                    addQueryParameter("without[]", without.id.toString())
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
            it.type == type && it.name.trim().lowercase() == query
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
            Filter.Header("Author and Scanlator filter require exact name. Add multiple by comma (,) separation"),
            Filter.Separator(),
            Filter.Header("Note: include only one tag/author/scanlator at a time for better results"),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = LinkedHashSet<MangaEntry>()

        document.select(".chapter-list a.name, .chapter-list .doujin_tags a").forEach { element ->
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

    private fun fetchSingleGenre(permalink: String, page: Int): Observable<MangasPage> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("tags")
            .addPathSegment("$permalink.json")
            .addQueryParameter("page", page.toString())
            .build()

        return client.newCall(GET(url, headers))
            .asObservableSuccess()
            .map { response ->
                val data = response.parseAs<BrowseTagResponse>()
                val entries = data.taggings.flatMap { chapter ->
                    chapter.getMangasFromChapter()
                }.distinct()
                    .map(MangaEntry::toSManga)

                MangasPage(
                    mangas = entries,
                    hasNextPage = data.hasNextPage(),
                )
            }
    }

    private fun fetchSingleAuthor(query: String): Observable<MangasPage> {
        val author = run {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("classes[]", "Author")
                .build()

            val document = client.newCall(GET(url, headers)).execute().asJsoup()

            document.selectFirst(".chapter-list a.name")
                ?.takeIf { it.ownText().lowercase() == query }
                ?.absUrl("href")
                ?.toHttpUrl()
                ?.pathSegments?.last()
                ?: throw Exception("Unknown Author: $query")
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("authors")
            .addPathSegment("$author.json")
            .build()

        return client.newCall(GET(url, headers))
            .asObservableSuccess()
            .map { response ->
                val data = response.parseAs<BrowseAuthorResponse>()
                val entries = LinkedHashSet<MangaEntry>()

                data.taggables.forEach { tag ->
                    MangaEntry(
                        url = "/${tag.directory!!}/${tag.permalink}",
                        title = tag.name,
                        cover = tag.cover?.let { buildCoverUrl(it) },
                    ).also(entries::add)
                }
                data.taggings.forEach { chapter ->
                    chapter.getMangasFromChapter()
                        .also(entries::addAll)
                }

                MangasPage(
                    mangas = entries.map(MangaEntry::toSManga),
                    hasNextPage = false,
                )
            }
    }

    private var scanlatorPermalink: String? = null
    private fun fetchSingleScanlator(query: String, page: Int): Observable<MangasPage> {
        val scanlator = if (page > 1 && scanlatorPermalink != null) {
            scanlatorPermalink!!
        } else {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("classes[]", "Scanlator")
                .build()

            val document = client.newCall(GET(url, headers)).execute().asJsoup()

            document.selectFirst(".chapter-list a.name")
                ?.takeIf { it.ownText().lowercase() == query }
                ?.absUrl("href")
                ?.toHttpUrl()
                ?.pathSegments?.last()
                ?.also { scanlatorPermalink = it }
                ?: throw Exception("Unknown Scanlator: $query")
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("scanlators")
            .addPathSegment("$scanlator.json")
            .addQueryParameter("page", page.toString())
            .build()

        return client.newCall(GET(url, headers))
            .asObservableSuccess()
            .map { response ->
                val data = response.parseAs<BrowseTagResponse>()
                val entries = data.taggings.flatMap { chapter ->
                    chapter.getMangasFromChapter()
                }.distinct()
                    .map(MangaEntry::toSManga)

                MangasPage(
                    mangas = entries,
                    hasNextPage = data.hasNextPage(),
                )
            }
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}.json", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (response.request.url.pathSegments[0] == "chapters") {
            return chapterDetailsParse(response)
        }

        val data = response.parseAs<MangaResponse>()
        val authors = LinkedHashSet<String>()
        val tags = LinkedHashSet<String>()
        val others = LinkedHashSet<Pair<String, String>>()
        var publishingStatus: Int = SManga.UNKNOWN

        data.tags.forEach { tag ->
            when (tag.type) {
                "Status" -> when (tag.permalink) {
                    "ongoing" -> SManga.ONGOING
                    "complete" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }.also { publishingStatus = it }

                "Author" -> authors.add(tag.name)
                "General" -> tags.add(tag.name)
                else -> others.add(tag.type to tag.name)
            }
        }

        data.taggings.filterIsInstance<MangaChapter>().forEach { tagging ->
            tagging.tags.forEach { tag ->
                when (tag.type) {
                    "Author" -> authors.add(tag.name)
                    "General" -> tags.add(tag.name)
                    "Series", "Scanlator", "Doujin", "Anthology" -> {}
                    else -> others.add(tag.type to tag.name)
                }
            }
        }

        return SManga.create().apply {
            title = data.name
            author = authors.joinToString()
            artist = author
            description = buildString {
                data.description?.let {
                    val desc = Jsoup.parseBodyFragment(
                        decodeUnicode(it),
                    )
                    desc.select("a").remove()

                    append(desc.wholeText())
                }

                if (isNotEmpty() && !endsWith("\n")) {
                    append("\n")
                }

                for ((type, values) in others.groupBy { it.first }) {
                    append("\n", type, ":\n")
                    values.forEach { append("\t• ", it.second, "\n") }
                }
                if (data.aliases.isNotEmpty()) {
                    append("\nAliases:\n")
                    data.aliases.forEach { append("\t• ", it, "\n") }
                }
            }.trim()
            genre = tags.joinToString()
            status = publishingStatus
            thumbnail_url = data.cover?.let { buildCoverUrl(it) }
        }
    }

    private fun decodeUnicode(input: String): String {
        return UNICODE_REGEX.replace(input) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            val char = hexCode.toInt(16).toChar()
            char.toString()
        }
    }

    private fun chapterDetailsParse(response: Response): SManga {
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
//        // TODO: correct mirror url
//        return HttpUrl.Builder().apply {
//            scheme("https")
//            host("0ms.dev")
//            addPathSegment("mirrors")
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
            )
            .fragment(COVER_URL_FRAGMENT)
            .build()
            .toString()
    }

    private fun buildChapterCoverFetchUrl(permalink: String): String {
        return HttpUrl.Builder().apply {
            scheme("http")
            host(COVER_FETCH_HOST)
            addQueryParameter("permalink", permalink)
        }.build().toString()
    }

    private fun fetchCoverUrlInterceptor(chain: Interceptor.Chain): Response {
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

    private fun coverInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        return if (request.url.fragment == COVER_URL_FRAGMENT) {
            coverClient.newCall(request).execute()
        } else {
            chain.proceed(request)
        }
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
private const val COVER_URL_FRAGMENT = "thumbnail"
private val CHAPTER_SLUG_REGEX = Regex("""(.*?)_(ch[0-9_]+|volume_[0-9_\w]+)""")
private val UNICODE_REGEX = Regex("\\\\u([0-9A-Fa-f]{4})")
