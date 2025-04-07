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

open class Dynasty : HttpSource(), ConfigurableSource {

    override val name = "Dynasty Scans"

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
        return GET("$baseUrl/$CHAPTERS_DIR/added.json?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<BrowseResponse>()
        val entries = LinkedHashSet<MangaEntry>()

        data.chapters.forEach { chapter ->
            var isSeries = false

            chapter.tags.forEach { tag ->
                if (tag.type in listOf(SERIES_TYPE, ANTHOLOGY_TYPE, DOUJIN_TYPE, ISSUE_TYPE)) {
                    MangaEntry(
                        url = "/${tag.directory}/${tag.permalink}",
                        title = tag.name,
                        cover = getCoverUrl(tag.directory, tag.permalink),
                    ).also(entries::add)

                    // true if an associated series is found
                    isSeries = isSeries || tag.type == SERIES_TYPE
                }
            }

            // individual chapter if no linked series
            // mostly the case for uploaded doujins
            if (!isSeries) {
                MangaEntry(
                    url = "/$CHAPTERS_DIR/${chapter.permalink}",
                    title = chapter.title,
                    cover = buildChapterCoverFetchUrl(chapter.permalink),
                ).also(entries::add)
            }
        }

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = data.hasNextPage(),
        )
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("deeplink:")) {
            var (_, directory, permalink) = query.split(":", limit = 3)

            if (directory == CHAPTERS_DIR) {
                val seriesPermalink = CHAPTER_SLUG_REGEX.find(permalink)?.groupValues?.get(1)

                if (seriesPermalink != null) {
                    directory = SERIES_DIR
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
        }

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { searchMangaParse(it, filters) }
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
            Filter.Header("Note: Author, Scanlator and Pairing filters require exact name. You can add multiple by comma (,) separation"),
        )
    }

    // lazy because extension inspector doesn't have implementation
    private val lruCache by lazy { LruCache<String, Int>(15) }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val typeFilter = filters.firstInstance<TypeFilter>()
            .also {
                if (it.checked.isEmpty()) {
                    throw Exception("Select at least one type")
                }
            }

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

        // series and doujin results are best when chapters are included as type so keep track of this
        var seriesSelected = false
        var doujinSelected = false
        var chapterSelected = false

        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.trim())
            filters.firstInstance<SortFilter>().also {
                addQueryParameter("sort", it.sort)
            }
            typeFilter.also {
                it.checked.forEach { type ->
                    seriesSelected = seriesSelected || type == SERIES_TYPE
                    doujinSelected = doujinSelected || type == DOUJIN_TYPE
                    chapterSelected = chapterSelected || type == CHAPTER_TYPE

                    addQueryParameter("classes[]", type)
                }
            }

            // series and doujin results are best when chapters are included
            // they will be filtered client side in `searchMangaParse`
            if ((seriesSelected || doujinSelected) && !chapterSelected) {
                addQueryParameter("classes[]", CHAPTER_TYPE)
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

    private fun searchMangaParse(response: Response, filters: FilterList): MangasPage {
        val typeFilter = filters.firstInstance<TypeFilter>()
        val includedSeries = typeFilter.checked.contains(SERIES_TYPE)
        val includedChapters = typeFilter.checked.contains(CHAPTER_TYPE)
        val includedDoujins = typeFilter.checked.contains(DOUJIN_TYPE)

        val document = response.asJsoup()
        val entries = LinkedHashSet<MangaEntry>()

        // saves the first entry found
        // returned if everything was filtered out to avoid "No Results found" error
        var firstEntry: MangaEntry? = null

        document.select(
            ".chapter-list a.name[href~=/($SERIES_DIR|$ANTHOLOGIES_DIR|$CHAPTERS_DIR|$DOUJINS_DIR|$ISSUES_DIR)/], " +
                ".chapter-list .doujin_tags a[href~=/$DOUJINS_DIR/]",
        ).forEach { element ->
            var (directory, permalink) = element.absUrl("href")
                .toHttpUrl().pathSegments
                .let { it[0] to it[1] }
            var title = element.ownText()

            if (directory == CHAPTERS_DIR) {
                val seriesPermalink = CHAPTER_SLUG_REGEX.find(permalink)?.groupValues?.get(1)

                if (seriesPermalink != null) {
                    directory = SERIES_DIR
                    permalink = seriesPermalink
                    title = seriesPermalink.permalinkToTitle()
                }
            }

            val entry = MangaEntry(
                url = "/$directory/$permalink",
                title = title,
                cover = getCoverUrl(directory, permalink),
            )

            if (firstEntry == null) {
                firstEntry = entry
            }

            // since we convert chapters to their series counterpart, and select doujins from chapters
            // it is possible to get a certain type even if it is unselected from filters
            // so don't include in that case
            if ((!includedSeries && directory == SERIES_DIR) ||
                (!includedChapters && directory == CHAPTERS_DIR) ||
                (!includedDoujins && directory == DOUJINS_DIR)
            ) {
                return@forEach
            }

            entries.add(entry)
        }

        // avoid "No Results found" error in case everything was filtered out from above check
        if (entries.isEmpty()) {
            firstEntry?.also { entries.add(it) }
        }

        val hasNextPage = document.selectFirst(".pagination [rel=next]") != null

        return MangasPage(
            mangas = entries.map(MangaEntry::toSManga),
            hasNextPage = hasNextPage,
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaPath = "$baseUrl${manga.url}".toHttpUrl().pathSegments

        assert(
            mangaPath.size == 2 &&
                mangaPath[0] in listOf(SERIES_DIR, ANTHOLOGIES_DIR, DOUJINS_DIR, ISSUES_DIR, CHAPTERS_DIR),
        ) { "Migrate to Dynasty Scans to update url" }

        val (directory, permalink) = mangaPath.let { it[0] to it[1] }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(directory)
            .addPathSegment("$permalink.json")
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (response.request.url.pathSegments[0] == CHAPTERS_DIR) {
            return chapterDetailsParse(response)
        }

        val data = response.parseAs<MangaResponse>()

        val authors = LinkedHashSet<String>()
        val tags = LinkedHashSet<String>()
        val others = LinkedHashSet<Pair<String, String>>()
        val publishingStatus = LinkedHashSet<String>()

        data.tags.forEach { tag ->
            when (tag.type) {
                "Author" -> authors.add(tag.name)
                "General" -> tags.add(tag.name)
                "Status" -> {
                    publishingStatus.add(tag.name)
                    others.add(tag.type to tag.name)
                }
                else -> others.add(tag.type to tag.name)
            }
        }

        data.taggings.filterIsInstance<MangaChapter>().forEach { tagging ->
            tagging.tags.forEach { tag ->
                when (tag.type) {
                    "Author" -> authors.add(tag.name)
                    "General" -> tags.add(tag.name)
                    SERIES_TYPE, DOUJIN_TYPE, ANTHOLOGY_TYPE, ISSUE_TYPE, "Scanlator" -> {}
                    else -> others.add(tag.type to tag.name)
                }
            }
        }

        return SManga.create().apply {
            title = data.name
            author = if (authors.size > AUTHORS_UPPER_LIMIT) {
                authors.take(AUTHORS_UPPER_LIMIT)
                    .joinToString(postfix = "...")
            } else {
                authors.joinToString()
            }
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

                if (authors.size > AUTHORS_UPPER_LIMIT) {
                    others.addAll(authors.map { "Author" to it })
                }

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
            status = when {
                publishingStatus.contains("Ongoing") -> SManga.ONGOING
                publishingStatus.contains("Completed") -> SManga.COMPLETED
                publishingStatus.contains("On Hiatus") -> SManga.ON_HIATUS
                publishingStatus.contains("Licensed") -> SManga.LICENSED
                listOf("Dropped", "Cancelled", "Not Updated", "Abandoned", "Removed")
                    .any { publishingStatus.contains(it) } -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = data.cover?.let { buildCoverUrl(it) }
        }
    }

    private fun decodeUnicode(input: String): String {
        return UNICODE_REGEX.replace(input) { matchResult ->
            matchResult.groupValues[1]
                .toInt(16)
                .toChar()
                .toString()
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
                append("Type: ", CHAPTER_TYPE, "\n\n")
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
        return if (manga.url.contains("/$CHAPTERS_DIR/")) {
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
                var chapterName = header?.let { "$it $title" } ?: title
                if (data.type != SERIES_TYPE) {
                    chapterName += tags.filter { it.type == "Author" }
                        .joinToString(prefix = " by ", separator = " and ") { it.name }
                }
                SChapter.create().apply {
                    url = "/$CHAPTERS_DIR/$permalink"
                    name = chapterName
                    scanlator = tags.filter { it.type == "Scanlator" }.joinToString { it.name }
                    date_upload = dateFormat.tryParse(releasedOn)
                }.also(chapterList::add)
            }
        }

        return if (data.type != DOUJIN_TYPE) {
            chapterList.asReversed()
        } else {
            chapterList
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterPath = "$baseUrl${chapter.url}".toHttpUrl().pathSegments

        assert(
            chapterPath.size == 2 &&
                chapterPath[0] == CHAPTERS_DIR,
        ) { "Refresh Chapter List" }

        val permalink = chapterPath[1]

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(CHAPTERS_DIR)
            .addPathSegment("$permalink.json")
            .build()

        return GET(url, headers)
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

    private val covers: Map<String, Map<String, String>> by lazy {
        this::class.java
            .getResourceAsStream("/assets/covers.json")!!
            .bufferedReader().use { it.readText() }
            .parseAs()
    }

    private fun getCoverUrl(directory: String?, permalink: String): String? {
        directory ?: return null

        if (directory == CHAPTERS_DIR) {
            return buildChapterCoverFetchUrl(permalink)
        }

        val file = covers[directory]?.get(permalink)
            ?: return null

        return buildCoverUrl(file)
    }

    private fun buildCoverUrl(file: String): String {
        val path = "$baseUrl$file".toHttpUrl()
            .encodedPath
            .removePrefix("/")

        return baseUrl.toHttpUrl()
            .newBuilder()
            .addEncodedPathSegments(path)
            .fragment(COVER_URL_FRAGMENT)
            .build()
            .toString()
    }

    private fun buildChapterCoverFetchUrl(permalink: String): String {
        return HttpUrl.Builder().apply {
            scheme("https")
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
            addPathSegment(CHAPTERS_DIR)
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
        return split('_')
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException()
}
