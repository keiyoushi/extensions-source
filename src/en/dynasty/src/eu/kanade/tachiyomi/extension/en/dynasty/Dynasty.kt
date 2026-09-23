package eu.kanade.tachiyomi.extension.en.dynasty

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.head
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getArrayOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class Dynasty :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    override val supportsRelatedMangas = true

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this
        .addInterceptor(::fetchCoverUrlInterceptor)
        .rateLimit(1) { it.fragment != COVER_URL_FRAGMENT }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) {
            val homeHeaders = headers.newBuilder()
                .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val document = client.get(baseUrl, homeHeaders).asJsoup()

            val entries = document
                .select("h4:contains(Most Popular of Past 7 Days) ~ ul.cover-list a.thumbnail")
                .mapNotNull { element ->
                    val permalink = element.absUrl("href").toHttpUrl().pathSegments.getOrNull(1)
                        ?: return@mapNotNull null
                    val (directory, resolvedPermalink) = resolveEntryPath(CHAPTERS_DIR, permalink)

                    MangaEntry(
                        url = "/$directory/$resolvedPermalink",
                        title = resolvedPermalink.permalinkToTitle(),
                        cover = getCachedCoverUrl(directory, resolvedPermalink),
                    )
                }
                .distinct()

            return MangasPage(entries.map(MangaEntry::toSManga), hasNextPage = true)
        }

        val data = client.get("$baseUrl/$CHAPTERS_DIR/added.json?page=${page - 1}")
            .parseAs<BrowseResponse>()

        return MangasPage(parseAddedChapters(data).map(MangaEntry::toSManga), data.hasNextPage())
    }

    private fun parseAddedChapters(data: BrowseResponse): List<MangaEntry> {
        val entries = LinkedHashSet<MangaEntry>()

        data.chapters.forEach { chapter ->
            var isSeries = false

            chapter.tags.forEach { tag ->
                if (tag.type in MANGA_TYPES) {
                    MangaEntry(
                        url = "/${tag.directory}/${tag.permalink}",
                        title = tag.name,
                        cover = getCachedCoverUrl(tag.directory, tag.permalink),
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

        return entries.toList()
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val typeFilter = filters.firstInstance<TypeFilter>()
            .also {
                if (it.checked.isEmpty()) {
                    throw Exception("Select at least one type")
                }
            }

        val includedSeries = typeFilter.checked.contains(SERIES_TYPE)
        val includedChapters = typeFilter.checked.contains(CHAPTER_TYPE)
        val includedDoujins = typeFilter.checked.contains(DOUJIN_TYPE)

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

        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.trim())
            filters.firstInstance<SortFilter>().also {
                if (it.sort == SMART_SORT) {
                    val sort = if (query.isNotBlank()) {
                        BEST_MATCH
                    } else {
                        RELEASED_ON
                    }
                    addQueryParameter("sort", sort)
                } else {
                    addQueryParameter("sort", it.sort)
                }
            }
            typeFilter.checked.forEach { type ->
                addQueryParameter("classes[]", type)
            }

            // series and doujin results are best when chapters are included
            // they will be filtered client side below
            if ((includedSeries || includedDoujins) && !includedChapters) {
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

        val document = client.get(url).asJsoup()

        val parsed = parseSearchEntries(document)
        val entries = parsed.filterNot { entry ->
            (!includedSeries && entry.url.startsWith("/$SERIES_DIR/")) ||
                (!includedChapters && entry.url.startsWith("/$CHAPTERS_DIR/")) ||
                (!includedDoujins && entry.url.startsWith("/$DOUJINS_DIR/"))
        }

        // avoid "No Results found" error in case everything was filtered out from above check
        val mangas = entries.ifEmpty { listOfNotNull(parsed.firstOrNull()) }

        return MangasPage(
            mangas = mangas.map(MangaEntry::toSManga),
            hasNextPage = document.selectFirst(".pagination [rel=next]") != null,
        )
    }

    private fun parseSearchEntries(document: Document): List<MangaEntry> = document.select(
        ".chapter-list a.name[href~=/($SERIES_DIR|$ANTHOLOGIES_DIR|$CHAPTERS_DIR|$DOUJINS_DIR|$ISSUES_DIR)/], " +
            ".chapter-list .doujin_tags a[href~=/$DOUJINS_DIR/]",
    ).mapNotNull { element ->
        val segments = element.absUrl("href").toHttpUrl().pathSegments
        if (segments.size < 2) {
            return@mapNotNull null
        }

        var (directory, permalink) = segments[0] to segments[1]
        var title = element.ownText()

        val (resolvedDirectory, resolvedPermalink) = resolveEntryPath(directory, permalink)
        if (resolvedDirectory != directory) {
            directory = resolvedDirectory
            permalink = resolvedPermalink
            title = resolvedPermalink.permalinkToTitle()
        }

        MangaEntry(
            url = "/$directory/$permalink",
            title = title,
            cover = getCachedCoverUrl(directory, permalink),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || path.size <= 1) {
            return null
        }

        val (directory, permalink) = resolveEntryPath(path[0], path[1])

        if (directory !in MANGA_DIRS) {
            return null
        }

        return MangaEntry(
            url = "/$directory/$permalink",
            title = permalink.permalinkToTitle(),
            cover = getCachedCoverUrl(directory, permalink),
        ).toSManga()
    }

    // resolves a chapter url to its linked series when possible
    private fun resolveEntryPath(directory: String, permalink: String): Pair<String, String> {
        if (directory != CHAPTERS_DIR) {
            return directory to permalink
        }

        val seriesPermalink = CHAPTER_SLUG_REGEX.find(permalink)?.groupValues?.get(1)
            ?: return directory to permalink

        return SERIES_DIR to seriesPermalink
    }

    private val lruCache = object : LinkedHashMap<String, Int>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 20
    }

    private suspend fun fetchTagId(query: String, type: String): Int? {
        val url = "$baseUrl/tags/suggest"
        val body = FormBody.Builder()
            .add("query", query)
            .build()

        val data = client.post(url, body)
            .parseAs<List<TagSuggest>>()

        return data.firstOrNull {
            it.type == type && it.name.trim().lowercase() == query
        }?.id
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaPath = "$baseUrl${manga.url}".toHttpUrl().pathSegments

        assert(
            mangaPath.size == 2 &&
                mangaPath[0] in MANGA_DIRS,
        ) { "Migrate to Dynasty Scans to update url" }

        val (directory, permalink) = mangaPath.let { it[0] to it[1] }
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(directory)
            .addPathSegment("$permalink.json")
            .build()
        val response = client.get(url)

        if (directory == CHAPTERS_DIR) {
            val data = response.parseAs<ChapterResponse>()

            return SMangaUpdate(
                manga = chapterDetailsParse(data),
                chapters = listOf(individualChapterParse(data)),
            )
        }

        val data = response.parseAs<MangaResponse>()

        val updatedChapters = if (fetchChapters) {
            val chapterItems = data.taggings.toMutableList()
            var page = 2
            val limit = preferences.chapterFetchLimit

            while (page <= data.totalPages && page <= limit) {
                val pageUrl = url.newBuilder()
                    .addQueryParameter("page", page.toString())
                    .build()

                chapterItems += client.get(pageUrl).parseAs<MangaResponse>().taggings
                page += 1
            }

            parseChapterList(data.type, chapterItems)
        } else {
            chapters
        }

        return SMangaUpdate(
            manga = mangaDetailsParse(data),
            chapters = updatedChapters,
        )
    }

    private suspend fun mangaDetailsParse(data: MangaResponse): SManga {
        val authors = LinkedHashSet<Pair<String, String>>()
        val tags = LinkedHashSet<String>()
        val others = LinkedHashSet<Pair<String, String>>()
        val publishingStatus = LinkedHashSet<String>()

        data.tags.forEach { tag ->
            when (tag.type) {
                "Author" -> authors.add(tag.name to tag.permalink)

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
                    "Author" -> authors.add(tag.name to tag.permalink)
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
                    .joinToString(postfix = "...") { it.first }
            } else {
                authors.joinToString { it.first }
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
                    others.addAll(authors.map { "Author" to it.first })
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
            thumbnail_url = resolveThumbnail(data)
            memo = buildJsonObject {
                put("authors", authors.map { it.second }.toJsonElement())
            }
        }
    }

    private suspend fun resolveThumbnail(data: MangaResponse): String? {
        val newCover = data.cover?.let { buildCoverUrl(it) }
        val cachedCover = getCachedCoverUrl(data.directory, data.permalink)

        if (newCover == null || cachedCover == null) {
            return newCover?.let { getHDCoverUrlIfAvailable(it) } ?: cachedCover
        }

        // if the site's cover is the same file as the cached one, prefer the cached HD cover
        // to avoid making HEAD requests in `getHDCoverUrlIfAvailable`
        val path = cachedCover.toHttpUrl().pathSegments
        val tmpSDCover = cachedCover.toHttpUrl().newBuilder().apply {
            val file = path.last().substringBeforeLast(".") + ".jpg"
            setPathSegment(5, "medium")
            setPathSegment(path.size - 1, file)
        }.toString()

        return if (tmpSDCover == newCover) cachedCover else getHDCoverUrlIfAvailable(newCover)
    }

    private fun chapterDetailsParse(data: ChapterResponse): SManga {
        val authors = LinkedHashSet<Pair<String, String>>()
        val tags = LinkedHashSet<String>()
        val others = LinkedHashSet<Pair<String, String>>()

        data.tags.forEach { tag ->
            when (tag.type) {
                "Author" -> authors.add(tag.name to tag.permalink)
                "General" -> tags.add(tag.name)
                else -> others.add(tag.type to tag.name)
            }
        }

        return SManga.create().apply {
            title = data.title
            author = authors.joinToString { it.first }
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
            memo = buildJsonObject {
                put("authors", authors.map { it.second }.toJsonElement())
            }
        }
    }

    // ============================= Chapters ==============================

    private fun parseChapterList(type: String, chapters: List<ChapterItem>): List<SChapter> {
        var header: String? = null

        val chapterList = mutableListOf<SChapter>()

        chapters.forEach { item ->
            if (item is MangaChapterHeader) {
                header = item.header
                return@forEach
            }

            with(item as MangaChapter) {
                var chapterName = header?.let { "$it $title" } ?: title
                if (type != SERIES_TYPE) {
                    chapterName += tags.filter { it.type == "Author" }
                        .joinToString(prefix = " by ", separator = " and ") { it.name }
                }
                SChapter.create().apply {
                    url = "/$CHAPTERS_DIR/$permalink"
                    name = chapterName
                    scanlator = tags.filter { it.type == "Scanlator" }.joinToString { it.name }
                    date_upload = dateFormat.tryParseDate(releasedOn)
                }.also(chapterList::add)
            }
        }

        return if (type != DOUJIN_TYPE) {
            chapterList.asReversed()
        } else {
            chapterList
        }
    }

    private fun individualChapterParse(data: ChapterResponse): SChapter = SChapter.create().apply {
        url = "/$CHAPTERS_DIR/${data.permalink}"
        name = "Chapter"
        scanlator = data.tags.filter { it.type == "Scanlator" }.joinToString { it.name }
        date_upload = dateFormat.tryParseDate(data.releasedOn)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
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

        val data = client.get(url).parseAs<ChapterResponse>()

        return data.pages.mapIndexed { index, page ->
            Page(index, imageUrl = baseUrl + page.url)
        }
    }

    // ============================== Related ==============================

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val authorSlug = manga.memo.getArrayOrNull("authors")
            ?.map { it.string }
            ?.randomOrNull()
            ?: return emptyList()

        val data = client.get("$baseUrl/authors/$authorSlug.json").parseAs<AuthorResponse>()
        val related = LinkedHashSet<MangaEntry>()

        data.taggables.forEach { taggable ->
            related.add(
                MangaEntry(
                    url = "/${taggable.directory}/${taggable.permalink}",
                    title = taggable.name,
                    cover = getCachedCoverUrl(taggable.directory, taggable.permalink),
                ),
            )
        }

        data.taggings.forEach { chapter ->
            val tag = chapter.tags.firstOrNull { it.type in MANGA_TYPES } ?: return@forEach
            related.add(
                MangaEntry(
                    url = "/${tag.directory}/${tag.permalink}",
                    title = tag.name,
                    cover = getCachedCoverUrl(tag.directory, tag.permalink),
                ),
            )
        }

        return related.map { it.toSManga() }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = this::class.java.getResourceAsStream("/assets/tags.json")!!.parseAs<List<Tag>>()

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

    // ============================= Utilities =============================

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
        get() = (getString(CHAPTER_FETCH_LIMIT_PREF, CHAPTER_FETCH_LIMITS[0]) ?: CHAPTER_FETCH_LIMITS[0]).let {
            if (it == "all") {
                Int.MAX_VALUE
            } else {
                it.toInt()
            }
        }

    private val covers: Map<String, Map<String, String>> by lazy {
        this::class.java.getResourceAsStream("/assets/covers.json")!!.parseAs()
    }

    private fun getCachedCoverUrl(directory: String?, permalink: String): String? {
        directory ?: return null

        if (directory == CHAPTERS_DIR) {
            return buildChapterCoverFetchUrl(permalink)
        }

        val file = covers[directory]?.get(permalink)
            ?: return null

        return buildCoverUrl(file)
    }

    private suspend fun getHDCoverUrlIfAvailable(coverUrl: String): String {
        val httpUrl = coverUrl.toHttpUrl()
        val path = httpUrl.pathSegments

        if (path.size == 7 && path[5] == "medium") {
            COVER_EXTENSIONS.forEach { format ->
                val newUrl = httpUrl.newBuilder().apply {
                    val file = path.last().substringBeforeLast(".") + ".$format"
                    setPathSegment(5, "original")
                    setPathSegment(path.size - 1, file)
                }.build()

                if (client.head(newUrl, ensureSuccess = false).use { it.isSuccessful }) {
                    return newUrl.toString()
                }
            }
        }

        return coverUrl
    }

    private fun buildCoverUrl(file: String): String {
        val path = "$baseUrl$file".toHttpUrl()
            .encodedPath
            .removePrefix("/")

        return baseUrl.toHttpUrl().newBuilder().apply {
            if (!path.startsWith("system/")) {
                addEncodedPathSegments("system/tag_contents_covers/000")
            }
            addEncodedPathSegments(path)
            fragment(COVER_URL_FRAGMENT)
        }.toString()
    }

    private fun buildChapterCoverFetchUrl(permalink: String): String = HttpUrl.Builder().apply {
        scheme("https")
        host(COVER_FETCH_HOST)
        addQueryParameter("permalink", permalink)
    }.build().toString()

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

        val page = client.newCall(
            Request.Builder()
                .url(chapterUrl)
                .headers(headers)
                .build(),
        ).execute()
            .parseAs<ChapterResponse>()
            .pages.first()

        val url = buildCoverUrl(page.url)

        val newRequest = request.newBuilder()
            .url(url)
            .build()

        return chain.proceed(newRequest)
    }

    private fun String.permalinkToTitle(): String = split('_')
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }

    private fun decodeUnicode(input: String): String = UNICODE_REGEX.replace(input) { matchResult ->
        matchResult.groupValues[1]
            .toInt(16)
            .toChar()
            .toString()
    }
}
