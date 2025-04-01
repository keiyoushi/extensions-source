package eu.kanade.tachiyomi.extension.en.dynasty

import android.content.SharedPreferences
import android.util.LruCache
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.use
import org.jsoup.Jsoup
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Dynasty : HttpSource(), ConfigurableSource {

    override val name = "Dynasty"

    override val lang = "en"

    override val baseUrl = "https://dynasty-scans.com"

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

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
        val entries = LinkedHashSet<MangaEntry>()

        data.chapters.flatMapTo(entries, ::getMangasFromChapter)

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = data.hasNextPage(),
        )
    }

    private fun getMangasFromChapter(chapter: BrowseChapter): List<MangaEntry> {
        val entries = mutableListOf<MangaEntry>()
        var isSeries = false

        chapter.tags.forEach { tag ->
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
                url = "/chapters/${chapter.permalink}",
                title = chapter.title,
                cover = buildChapterCoverFetchUrl(chapter.permalink),
            ).also(entries::add)
        }

        return entries
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
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
            val tagFilter = filters.firstInstance<TagFilter>()
            val authorFilter = filters.firstInstance<AuthorFilter>()
            val scanlatorFilter = filters.firstInstance<ScanlatorFilter>()
            val pairingFilter = filters.firstInstance<PairingFilter>()

            when {
                // only one tag included
                tagFilter.included.size == 1 &&
                    tagFilter.excluded.isEmpty() &&
                    authorFilter.values.isEmpty() &&
                    scanlatorFilter.values.isEmpty() &&
                    pairingFilter.values.isEmpty()
                -> return fetchSingleTag(tagFilter.included.first().permalink, page)

                // only one author specified
                tagFilter.isEmpty() &&
                    authorFilter.values.size == 1 &&
                    scanlatorFilter.values.isEmpty() &&
                    pairingFilter.values.isEmpty()
                -> return fetchSingleAuthor(authorFilter.values.first())

                // only one scanlator specified
                tagFilter.isEmpty() &&
                    authorFilter.values.isEmpty() &&
                    scanlatorFilter.values.size == 1 &&
                    pairingFilter.values.isEmpty()
                -> return fetchSingleScanlator(scanlatorFilter.values.first(), page)
            }
        }

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { searchMangaParse(it, filters) }
    }

    // lazy because extension inspector doesn't have implementation
    private val lruCache by lazy { LruCache<String, Int>(15) }

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
        val pairing = filters.firstInstance<PairingFilter>().values.map { pairing ->
            lruCache[pairing]
                ?: fetchTagId(pairing, "Pairing")
                    ?.also { lruCache.put(pairing, it) }
                ?: throw Exception("Unknown Pairing: $pairing")
        }

        // series results are best when chapters are included as type so keep track of this
        var seriesSelected = false
        var chapterSelected = false

        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.trim())
            filters.firstInstance<SortFilter>().also {
                addQueryParameter("sort", it.sort)
            }
            filters.firstInstance<TypeFilter>().also {
                it.checked.forEach { type ->
                    if (type == "Series") {
                        seriesSelected = true
                    } else if (type == "Chapter") {
                        chapterSelected = true
                    }

                    addQueryParameter("classes[]", type)
                }
            }

            // series results are best when chapters are included
            // they will be filtered client side in `searchMangaParse`
            if (seriesSelected && !chapterSelected) {
                addQueryParameter("classes[]", "Chapter")
            }

            filters.firstInstance<TagFilter>().also {
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
            pairing.forEach { pairing ->
                addQueryParameter("with[]", pairing.toString())
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
            .parseAs<List<Tag>>()

        return FilterList(
            SortFilter(),
            TypeFilter(),
            Filter.Header("Note: Sort and Type may not always work"),
            Filter.Separator(),
            TagFilter(tags),
            AuthorFilter(),
            ScanlatorFilter(),
            PairingFilter(),
            Filter.Header("Author, Scanlator and Pairing filters require exact name. You can add multiple by comma (,) separation"),
            Filter.Header("Note: include only one tag/author/scanlator/pairing at a time for better results"),
        )
    }

    private fun searchMangaParse(response: Response, filters: FilterList): MangasPage {
        val typeFilter = filters.firstInstance<TypeFilter>()
        val includedSeries = typeFilter.checked.contains("Series")
        val includedChapters = typeFilter.checked.contains("Chapter")
        val includedDoujins = typeFilter.checked.contains("Doujin")

        val document = response.asJsoup()
        val entries = LinkedHashSet<MangaEntry>()

        // saves the first entry found
        // returned if everything was filtered out to avoid "No Results found" error
        var firstEntry: MangaEntry? = null

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

            val entry = MangaEntry(
                url = "/$directory/$permalink",
                title = title,
                cover = getCoverUrl(directory, permalink),
            )

            // since we convert chapters to their series counterpart, and select doujins from chapters
            // it is possible to get a certain type even if it is unselected from filters
            // so don't include in that case
            if ((!includedSeries && directory == "series") ||
                (!includedChapters && directory == "chapters") ||
                (!includedDoujins && directory == "doujins")
            ) {
                return@forEach
            }

            if (firstEntry == null) {
                firstEntry = entry
            }

            entries.add(entry)
        }

        // avoid "No Results found" error in case everything was filtered out from above check
        if (entries.isEmpty() && firstEntry != null) {
            entries.add(firstEntry!!)
        }

        val hasNextPage = document.selectFirst("div.pagination > ul > li.active + li > a") != null

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = hasNextPage,
        )
    }

    private fun fetchSingleTag(permalink: String, page: Int): Observable<MangasPage> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("tags")
            .addPathSegment("$permalink.json")
            .addQueryParameter("page", page.toString())
            .build()

        return client.newCall(GET(url, headers))
            .asObservableSuccess()
            .map { response ->
                val data = response.parseAs<BrowseTagResponse>()
                val entries = LinkedHashSet<MangaEntry>()

                data.taggings.flatMapTo(entries, ::getMangasFromChapter)

                MangasPage(
                    mangas = entries.map(MangaEntry::toSManga),
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

                data.taggables.mapTo(entries) { tag ->
                    MangaEntry(
                        url = "/${tag.directory!!}/${tag.permalink}",
                        title = tag.name,
                        cover = tag.cover?.let { buildCoverUrl(it) },
                    )
                }

                data.taggings.flatMapTo(entries, ::getMangasFromChapter)

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
                val entries = LinkedHashSet<MangaEntry>()

                data.taggings.flatMapTo(entries, ::getMangasFromChapter)

                MangasPage(
                    mangas = entries.map(MangaEntry::toSManga),
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
                    // when manga is both ongoing and licenced, prefer ongoing
                    "licensed" -> if (publishingStatus == SManga.ONGOING) {
                        SManga.ONGOING
                    } else {
                        SManga.LICENSED
                    }
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
                val prefChapterFetchLimit = preferences.chapterFetchLimit
                if (prefChapterFetchLimit < data.totalPages) {
                    append("IMPORTANT: Only first $prefChapterFetchLimit pages of chapter list will be fetched. You can change this in extension settings.\n\n")
                }

                data.description?.let {
                    val desc = Jsoup.parseBodyFragment(
                        decodeUnicode(it),
                    )
                    desc.select("a").remove()

                    append(desc.wholeText().trim())
                    append("\n\n")
                }

                append("Type: ", data.type, "\n\n")

                for ((type, values) in others.groupBy { it.first }) {
                    append(type, ":\n")
                    values.forEach { append("• ", it.second, "\n") }
                    append("\n")
                }
                if (data.aliases.isNotEmpty()) {
                    append("Aliases:\n")
                    data.aliases.forEach { append("• ", it, "\n") }
                    append("\n")
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
        val data = response.parseAs<ChapterResponse>()

        val authors = LinkedHashSet<String>()
        val tags = LinkedHashSet<String>()
        val others = LinkedHashSet<Pair<String, String>>()

        data.tags.forEach { tag ->
            when (tag.type) {
                "Author" -> authors.add(tag.name)
                "General" -> tags.add(tag.name)
                else -> others.add(tag.type to tag.name)
            }
        }

        return SManga.create().apply {
            title = data.title
            author = authors.joinToString()
            artist = author
            description = buildString {
                append("Type: ", "Chapter", "\n\n")
                for ((type, values) in others.groupBy { it.first }) {
                    append(type, ":\n")
                    values.forEach { append("• ", it.second, "\n") }
                    append("\n")
                }
                append("Released: ", data.releasedOn)
            }.trim()
            genre = tags.joinToString()
            thumbnail_url = buildCoverUrl(data.pages.first().url)
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.url.contains("/chapters/")) {
            Observable.just(
                listOf(
                    SChapter.create().apply {
                        url = manga.url
                        name = "Chapter"
                        date_upload = dateFormat.tryParse(
                            manga.description
                                ?.substringAfter("Released:", ""),
                        )
                    },
                ),
            )
        } else {
            super.fetchChapterList(manga)
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaResponse>()
        val chapters = data.taggings.toMutableList()

        var page = 2
        val limit = preferences.chapterFetchLimit

        while (page <= data.totalPages && page <= limit) {
            val url = response.request.url.newBuilder()
                .addQueryParameter("page", page.toString())
                .build()

            chapters += client.newCall(GET(url, headers)).execute()
                .parseAs<MangaResponse>().taggings
            page += 1
        }

        var header: String? = null

        val chapterList = mutableListOf<SChapter>()

        chapters.forEach { item ->
            if (item is MangaChapterHeader) {
                header = item.header
                return@forEach
            }

            with(item as MangaChapter) {
                SChapter.create().apply {
                    url = "/chapters/$permalink"
                    name = header?.let { "$it $title" } ?: title
                    scanlator = tags.filter { it.type == "Scanlator" }.joinToString { it.name }
                    date_upload = dateFormat.tryParse(releasedOn)
                }.also(chapterList::add)
            }
        }

        return if (data.type != "Doujin") {
            chapterList.asReversed()
        } else {
            chapterList
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}.json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterResponse>()

        return data.pages.mapIndexed { index, page ->
            Page(index, imageUrl = baseUrl + page.url)
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = CHAPTER_FETCH_LIMIT_PREF
            title = "Chapters Fetch Limit"
            entries = CHAPTER_FETCH_LIMITS.map { "$it pages" }.toTypedArray()
            entryValues = CHAPTER_FETCH_LIMITS
            setDefaultValue(CHAPTER_FETCH_LIMITS[0])
            summary = """
                Limits how many pages of an entry are fetched for chapter list
                Mostly applies to Doujins

                More pages mean slower loading of chapter list

                Currently fetching %s
            """.trimIndent()
        }.also(screen::addPreference)
    }

    private val SharedPreferences.chapterFetchLimit: Int
        get() = getString(CHAPTER_FETCH_LIMIT_PREF, CHAPTER_FETCH_LIMITS[0])!!.let {
            if (it == "all") {
                Int.MAX_VALUE
            } else {
                it.toInt()
            }
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

    override fun searchMangaParse(response: Response): MangasPage {
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
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

private const val CHAPTER_FETCH_LIMIT_PREF = "chapterFetchLimit"
private val CHAPTER_FETCH_LIMITS = arrayOf("2", "5", "10", "all")
