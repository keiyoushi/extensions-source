package eu.kanade.tachiyomi.extension.all.xcomic

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element

@Source
abstract class XCOMIC :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    // ========================= Popular & Latest ==========================
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(DefaultSortFilter("field_score")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(DefaultSortFilter("field_update")))

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val idMatch = idQueryRegex.matchEntire(query.trim())
        if (idMatch != null) {
            val id = idMatch.groupValues[1]
            val manga = resolveIdLookup(id) ?: throw Exception("Entry id '$id' not found")
            return MangasPage(listOf(manga), false)
        }

        var sort: String? = null
        var contentRating = emptyList<String>()
        var types = emptyList<String>()
        var demographics = emptyList<String>()
        val incGenres = mutableListOf<String>()
        val excGenres = mutableListOf<String>()
        var incGenresMode: String? = null
        var excGenresMode: String? = null
        var releaseYearMin: Int? = null
        var releaseYearMax: Int? = null
        var incOLangs = emptyList<String>()
        var incTLangs = if (lang == "all") emptyList() else listOf(mapLangCode(lang))
        var origStatus = emptyList<String>()
        var chapCount = ""

        filters.forEach { filter ->
            when (filter) {
                is DefaultSortFilter -> sort = filter.sort
                is ContentRatingFilter -> contentRating = filter.selected
                is TypeFilter -> types = filter.selected
                is DemographicFilter -> demographics = filter.selected
                is FormatFilter -> {
                    incGenres.addAll(filter.included)
                    excGenres.addAll(filter.excluded)
                }
                is GenreGroupFilter -> {
                    incGenres.addAll(filter.included)
                    excGenres.addAll(filter.excluded)
                }
                is GenreInModeFilter -> incGenresMode = filter.selected
                is GenreExModeFilter -> excGenresMode = filter.selected
                is YearFilter -> {
                    filter.state.takeIf { it.isNotEmpty() }?.let { year ->
                        if (year.contains("-")) {
                            releaseYearMin = year.substringBefore("-").trim().toIntOrNull()
                            releaseYearMax = year.substringAfter("-").trim().toIntOrNull()
                        } else {
                            val y = year.trim().toIntOrNull()
                            releaseYearMin = y
                            releaseYearMax = y
                        }
                    }
                }
                is OriginalLanguageFilter -> incOLangs = filter.selected
                is TranslationLanguageFilter -> {
                    if (filter.selected.isNotEmpty() && lang == "all") {
                        incTLangs = filter.selected
                    }
                }
                is OriginalStatusFilter -> origStatus = filter.selected
                is SortFilter -> sort = filter.selected
                is ChapterCountFilter -> chapCount = filter.selected
                else -> {}
            }
        }

        val variables = ApiTitleSearchVariables(
            page = page,
            size = BROWSE_PAGE_SIZE,
            init = (page - 1) * BROWSE_PAGE_SIZE,
            sortby = sort,
            word = query.takeIf { it.isNotEmpty() } ?: "",
            where = "browse",
            releaseYearMin = releaseYearMin,
            releaseYearMax = releaseYearMax,
            incTypes = types,
            incDemographics = demographics,
            incContentRatings = contentRating,
            incGenres = incGenres,
            excGenres = excGenres,
            incGenresMode = incGenresMode?.takeIf { it.isNotEmpty() },
            excGenresMode = excGenresMode?.takeIf { it.isNotEmpty() },
            incOLangs = incOLangs,
            incTLangs = incTLangs,
            origStatus = origStatus,
            chapCount = chapCount.takeIf { it.isNotEmpty() },
            ignoreGlobalGenres = isIgnoreGenreBlocklist(),
        )

        return coroutineScope {
            val itemsDeferred = async {
                val payload = graphQLBody(query = TITLE_ITEMS_QUERY, variables = ApiTitleSearchWrapper(variables))
                client.post("$baseUrl/query/", payload).parseGraphQLAs<BrowseItemsData>()
            }
            val pagerDeferred = async {
                val payload = graphQLBody(query = TITLE_PAGER_QUERY, variables = ApiTitleSearchWrapper(variables))
                client.post("$baseUrl/query/", payload).parseGraphQLAs<BrowsePagerData>()
            }
            val mangas = itemsDeferred.await().items.mapNotNull { item ->
                item.data?.toSManga(baseUrl, ::cleanTitleIfNeeded)
            }
            MangasPage(mangas, pagerDeferred.await().pager.hasNextPage())
        }
    }

    // ============================== Filters ==============================
    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get("$baseUrl/search")
        val document = response.asJsoup()

        val filterMap = mutableMapOf<String, MutableList<Map<String, String>>>()
        document.select("details").forEach { details ->
            val summaryText = details.selectFirst("summary")?.text()?.lowercase() ?: return@forEach
            val category = when {
                "content rating" in summaryText -> "contentRatings"
                "type" in summaryText -> "types"
                "demographic" in summaryText -> "demographics"
                "genre" in summaryText -> "genres"
                else -> null
            } ?: return@forEach

            val options = extractFilterOptions(details).toMutableList()
            if (category == "genres") {
                val formatsHeader = details.select("div").firstOrNull { it.ownText() == "Formats" }
                val formats = formatsHeader?.nextElementSibling()?.let { extractFilterOptions(it) }.orEmpty()
                if (formats.isNotEmpty()) {
                    filterMap.getOrPut("formats") { mutableListOf() }.addAll(formats)
                    val formatValues = formats.map { it["value"] }.toSet()
                    options.removeAll { it["value"] in formatValues }
                }
            }
            filterMap.getOrPut(category) { mutableListOf() }.addAll(options)
        }

        if (filterMap["genres"].isNullOrEmpty() && filterMap["types"].isNullOrEmpty()) {
            throw Exception("Failed to fetch filters dynamically")
        }

        return filterMap.mapValues { it.value.distinctBy { v -> v["value"] } }.toJsonElement()
    }

    private fun extractFilterOptions(scope: Element): List<Map<String, String>> = scope.select("div[:]").mapNotNull { div ->
        val value = div.attr(":")
        val name = div.selectFirst("span")?.text()
        if (value.matches(filterValueRegex) && !name.isNullOrEmpty()) {
            mapOf("name" to name, "value" to value)
        } else {
            null
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val parsed = data?.parseAs<Map<String, List<Map<String, String>>>>() ?: emptyMap()

        fun extractList(key: String): List<Pair<String, String>> = parsed[key]?.mapNotNull { map ->
            val name = map["name"]
            val value = map["value"]
            if (name != null && value != null) name to value else null
        } ?: emptyList()

        val dynamicGenres = extractList("genres")
        val dynamicFormats = extractList("formats")
        val dynamicTypes = extractList("types")
        val dynamicDemographics = extractList("demographics")
        val dynamicContentRatings = extractList("contentRatings")

        return FilterList(
            buildList {
                if (parsed.isEmpty()) {
                    add(Filter.Header("Filters could not be fetched; only basic filters are available"))
                }
                add(SortFilter())
                if (dynamicContentRatings.isNotEmpty()) add(ContentRatingFilter(options = dynamicContentRatings))
                if (dynamicTypes.isNotEmpty()) add(TypeFilter(options = dynamicTypes))
                add(Filter.Separator())
                if (dynamicDemographics.isNotEmpty()) add(DemographicFilter(options = dynamicDemographics))
                if (dynamicGenres.isNotEmpty()) add(GenreGroupFilter(options = dynamicGenres))
                if (dynamicFormats.isNotEmpty()) add(FormatFilter(options = dynamicFormats))
                add(GenreInModeFilter())
                add(GenreExModeFilter())
                add(Filter.Separator())
                add(OriginalStatusFilter())
                add(OriginalLanguageFilter())
                if (lang == "all") add(TranslationLanguageFilter())
                add(ChapterCountFilter())
                add(Filter.Separator())
                add(YearFilter())
            },
        )
    }

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // Entries saved before the title/source split store a comic id; keep
        // resolving them directly through the comic node API.
        val legacyComic = fetchComicNode(manga.url)
        if (legacyComic != null) {
            val source = legacyComic.toChapterEdition(manga.url, manga.title)
            val includeSources = getIncludeSources().sorted().joinToString(",")
            val deduplicate = isDeduplicateChapters()
            val lastPublicAt = legacyComic.titleNode?.chapLastPublicAt
            val prevFetchedAt = manga.memo[MEMO_FETCHED_AT]?.string?.toLongOrNull() ?: 0L
            val prevLastPublic = manga.memo[MEMO_LAST_PUBLIC]?.string?.toLongOrNull() ?: 0L
            val chaptersCurrent = isChapterCacheCurrent(manga, lastPublicAt, includeSources, deduplicate)
            val chapterList = if (!fetchChapters) {
                chapters
            } else if (chaptersCurrent) {
                postProcessChapters(chapters)
            } else {
                fetchAllChapters(listOf(source))
            }
            val details = if (fetchDetails) {
                legacyComic.titleNode?.toSManga(
                    baseUrl,
                    ::cleanTitleIfNeeded,
                    url = manga.url,
                    comic = legacyComic,
                    uploaders = chapterList.mapNotNull { it.uploader() }.distinct(),
                ) ?: manga
            } else {
                manga
            }
            if (fetchChapters) {
                val fetchedAt = if (chaptersCurrent) prevFetchedAt else System.currentTimeMillis()
                val updatedAt = if (chaptersCurrent) prevLastPublic.takeIf { it > 0L } else lastPublicAt
                details.attachChapterMemo(
                    fetchedAt,
                    updatedAt,
                    includeSources,
                    deduplicate,
                )
            }
            return SMangaUpdate(details, chapterList)
        }

        val titleNode = fetchTitleNode(manga.url)?.let { node ->
            node.mergedTitleId?.let { fetchTitleNode(it) } ?: node
        } ?: throw Exception("Title not found")

        val comicIds = titleNode.comicIds.orEmpty().filter { it.isNotBlank() }

        // Skip the chapter fan-out when neither the title nor chapter settings changed.
        val includeSources = getIncludeSources().sorted().joinToString(",")
        val deduplicate = isDeduplicateChapters()
        val prevFetchedAt = manga.memo[MEMO_FETCHED_AT]?.string?.toLongOrNull() ?: 0L
        val prevLastPublic = manga.memo[MEMO_LAST_PUBLIC]?.string?.toLongOrNull() ?: 0L
        val chaptersCurrent = isChapterCacheCurrent(manga, titleNode.chapLastPublicAt, includeSources, deduplicate)

        val targets = if (fetchChapters && !chaptersCurrent) resolveTargetComics(comicIds, manga.title) else null

        val chapterList = if (fetchChapters) {
            when {
                chaptersCurrent -> postProcessChapters(chapters)
                targets != null && targets.sources.isNotEmpty() -> fetchAllChapters(targets.sources)
                else -> emptyList()
            }
        } else {
            chapters
        }

        val details = if (fetchDetails) {
            val detailsComicId = targets?.bestComicId ?: comicIds.firstOrNull()
            val comic = detailsComicId?.let { fetchComicNode(it) }
            titleNode.toSManga(
                baseUrl,
                ::cleanTitleIfNeeded,
                comic = comic,
                uploaders = chapterList.mapNotNull { it.uploader() }.distinct(),
            )
        } else {
            manga
        }

        if (fetchChapters) {
            val (fetchedAt, lastPublic) = if (chaptersCurrent) {
                prevFetchedAt.takeIf { it > 0 } to prevLastPublic.takeIf { it > 0 }
            } else {
                System.currentTimeMillis() to titleNode.chapLastPublicAt
            }
            details.attachChapterMemo(fetchedAt, lastPublic, includeSources, deduplicate)
        }

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchTitleNode(id: String): TitleNode? {
        val payload = graphQLBody(query = TITLE_NODE_QUERY, variables = ApiTitleNodeVariables(id))
        val response = client.post("$baseUrl/query/", payload)
        return response.parseGraphQLAs<TitleNodeEnvelope>().response?.data
    }

    private suspend fun fetchComicNode(id: String): ComicNode? {
        val payload = graphQLBody(query = COMIC_NODE_QUERY, variables = ApiComicNodeVariables(id))
        val response = client.post("$baseUrl/query/", payload)
        return response.parseGraphQLAs<ComicNodeData>().response?.data
    }

    private suspend fun fetchComicProbe(id: String): ComicProbeData? {
        val payload = graphQLBody(query = COMIC_PROBE_QUERY, variables = ApiComicNodeVariables(id))
        val response = client.post("$baseUrl/query/", payload)
        return response.parseGraphQLAs<ComicProbeDataEnvelope>().response?.data
    }

    private fun isChapterCacheCurrent(
        manga: SManga,
        lastPublicAt: Long?,
        includeSources: String,
        deduplicate: Boolean,
    ): Boolean {
        val currentLastPublicAt = lastPublicAt?.takeIf { it > 0L } ?: return false
        val previousLastPublicAt = manga.memo[MEMO_LAST_PUBLIC]?.string?.toLongOrNull() ?: return false
        val fetchedAt = manga.memo[MEMO_FETCHED_AT]?.string?.toLongOrNull() ?: return false

        return fetchedAt > 0L &&
            previousLastPublicAt == currentLastPublicAt &&
            manga.memo[MEMO_CHAPTER_DATA_VERSION]?.string?.toIntOrNull() == CHAPTER_DATA_VERSION &&
            manga.memo[MEMO_DEDUPLICATE]?.string == deduplicate.toString() &&
            manga.memo[MEMO_SOURCES_FILTER]?.string.orEmpty() == includeSources
    }

    private class TitleTargets(val sources: List<ChapterEdition>, val bestComicId: String?)

    private suspend fun resolveTargetComics(comicIds: List<String>, fallbackName: String): TitleTargets {
        val probed = coroutineScope {
            comicIds.chunked(PROBE_BATCH).flatMap { batch ->
                batch.map { comicId -> async { comicId to fetchComicProbe(comicId) } }.awaitAll()
            }
        }.mapNotNull { (comicId, probe) -> probe?.let { comicId to it } }

        val matching = if (lang == "all") {
            emptyList()
        } else {
            val mapped = mapLangCode(lang)
            probed.filter { (_, probe) -> probe.isLive() && probe.translatedLanguage == mapped }
        }
        val sourceIds = if (lang == "all") comicIds else matching.map { it.first }
        val probesById = probed.toMap()
        val sources = sourceIds.map { comicId ->
            probesById[comicId]?.toChapterEdition(comicId, fallbackName)
                ?: ChapterEdition(comicId, fallbackName)
        }

        val best = if (lang == "all") {
            sourceIds.firstOrNull()
        } else {
            matching.maxByOrNull { (_, probe) -> probe.chapsNormal ?: 0 }?.first
        }
        return TitleTargets(disambiguateChapterEditionLabels(sources), best)
    }

    private fun disambiguateChapterEditionLabels(sources: List<ChapterEdition>): List<ChapterEdition> {
        val labelCounts = sources.groupingBy { it.label.trim().lowercase() }.eachCount()
        return sources.map { source ->
            val label = if (labelCounts[source.label.trim().lowercase()]!! > 1) {
                "${source.label} [${source.id.takeLast(6)}]"
            } else {
                source.label
            }
            ChapterEdition(source.id, label)
        }
    }

    private suspend fun resolveIdLookup(id: String): SManga? {
        fetchTitleNode(id)?.let { titleNode ->
            val resolved = titleNode.mergedTitleId?.let { fetchTitleNode(it) } ?: titleNode
            val comic = resolved.comicIds?.firstOrNull()?.let { fetchComicNode(it) }
            return resolved.toSManga(baseUrl, ::cleanTitleIfNeeded, comic = comic)
        }
        val comicNode = fetchComicNode(id) ?: return null
        return comicNode.titleNode?.toSManga(baseUrl, ::cleanTitleIfNeeded, comic = comicNode)
    }

    private fun SManga.attachChapterMemo(
        fetchedAt: Long?,
        lastPublicAt: Long?,
        sourcesFilter: String?,
        deduplicate: Boolean,
    ) {
        val existing = memo.jsonObject
        memo = buildJsonObject {
            existing.forEach { (key, value) -> put(key, value) }
            fetchedAt?.let { put(MEMO_FETCHED_AT, it.toString()) }
            lastPublicAt?.let { put(MEMO_LAST_PUBLIC, it.toString()) }
            sourcesFilter?.let { put(MEMO_SOURCES_FILTER, it) }
            put(MEMO_CHAPTER_DATA_VERSION, CHAPTER_DATA_VERSION.toString())
            put(MEMO_DEDUPLICATE, deduplicate.toString())
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        val comicId = when {
            segments.size >= 2 && segments[0] == "source" -> segments[1].substringBefore("-")
            segments.size >= 2 && segments[0] == "comic" && segments[1] != "_" -> segments[1].substringBefore("-")
            else -> null
        }
        if (comicId != null) {
            val comicNode = fetchComicNode(comicId) ?: return null
            return comicNode.titleNode?.toSManga(baseUrl, ::cleanTitleIfNeeded, comic = comicNode)
        }
        if (segments.size >= 2 && segments[0] == "title") {
            return resolveIdLookup(segments[1].substringBefore("-"))
        }
        return null
    }

    override fun getMangaUrl(manga: SManga): String {
        val urlPath = manga.memo["urlPath"]?.string
        return if (urlPath != null) "$baseUrl$urlPath" else "$baseUrl/title/${manga.url}"
    }

    // ============================= Chapters ==============================
    private suspend fun fetchAllChapters(sources: List<ChapterEdition>): List<SChapter> = coroutineScope {
        val deduplicate = isDeduplicateChapters()
        sources.map { source -> async { fetchChapterListPaged(source, deduplicate) } }
            .awaitAll()
            .flatten()
            .let { postProcessChapters(it) }
    }

    private fun postProcessChapters(chapters: List<SChapter>): List<SChapter> {
        val include = getIncludeSources()
        val filtered = if (include.isEmpty()) {
            chapters
        } else {
            chapters.filter { it.uploader()?.lowercase() in include }
        }
        val groupByEdition = isGroupByEdition()
        val groupByUploader = isGroupBySource()
        return when {
            groupByEdition && groupByUploader -> filtered.sortedWith(
                compareBy<SChapter> { it.comicId().orEmpty() }
                    .thenBy { it.uploader().orEmpty() }
                    .thenByDescending { it.chapter_number },
            )
            groupByEdition -> filtered.sortedWith(
                compareBy<SChapter> { it.comicId().orEmpty() }.thenByDescending { it.chapter_number },
            )
            groupByUploader -> filtered.sortedWith(
                compareBy<SChapter> { it.uploader().orEmpty() }.thenByDescending { it.chapter_number },
            )
            else -> filtered.sortedByDescending { it.chapter_number }
        }
    }

    private suspend fun fetchChapterListPaged(source: ChapterEdition, deduplicate: Boolean): List<SChapter> {
        val pageSize = if (deduplicate) 1000 else 100

        val firstPage = fetchChapterListPage(source, 1, deduplicate, pageSize)
        val allChapters = firstPage.chapters.toMutableList()
        val totalItems = firstPage.total ?: 0

        if (totalItems > pageSize && firstPage.hasNextPage) {
            val totalPages = (totalItems + pageSize - 1) / pageSize

            (2..totalPages).chunked(3).forEach { batch ->
                allChapters.addAll(
                    coroutineScope {
                        batch.map { pageNum ->
                            async { fetchChapterListPage(source, pageNum, deduplicate, pageSize).chapters }
                        }.awaitAll().flatten()
                    },
                )
            }
        }

        return allChapters
    }

    private suspend fun fetchChapterListPage(
        source: ChapterEdition,
        page: Int,
        deduplicate: Boolean,
        pageSize: Int,
    ): ChapterListPage {
        val select = ApiChapterListSelect(
            comicId = source.id,
            page = page,
            size = pageSize,
        )

        val payload = if (deduplicate) {
            graphQLBody(query = CHAPTER_UNIQ_LIST_QUERY, variables = ApiChapterListWrapper(select))
        } else {
            graphQLBody(query = CHAPTER_LIST_QUERY, variables = ApiChapterListWrapper(select))
        }
        val response = client.post("$baseUrl/query/", payload)

        val data = if (deduplicate) {
            response.parseGraphQLAs<ChapterListUniqData>().response
        } else {
            response.parseGraphQLAs<ChapterListData>().response
        }

        return ChapterListPage(
            chapters = data.items.map { it.data.toSChapter(source.id, source.label) },
            total = data.paging.total,
            hasNextPage = data.paging.hasNextPage(),
        )
    }

    private class ChapterListPage(
        val chapters: List<SChapter>,
        val total: Int?,
        val hasNextPage: Boolean,
    )

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val payload = graphQLBody(query = CHAPTER_PAGES_QUERY, variables = ApiChapterNodeVariables(chapter.url))
        val response = client.post("$baseUrl/query/", payload)
        val data = response.parseGraphQLAs<ChapterPagesData>().response.data

        return data.imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = if (url.startsWith("http")) url else "$baseUrl$url")
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val urlPath = chapter.memo["urlPath"]?.string
        return if (urlPath != null) "$baseUrl$urlPath" else "$baseUrl/chapter/${chapter.url}"
    }

    private fun cleanTitleIfNeeded(title: String): String {
        var tempTitle = title
        customRemoveTitle().takeIf { it.isNotEmpty() }?.let { customRegex ->
            runCatching {
                tempTitle = tempTitle.replace(Regex(customRegex), "")
            }
        }
        if (isRemoveTitleVersion()) {
            tempTitle = tempTitle.replace(titleRegex, "")
        }
        return tempTitle.trim()
    }

    // ============================ Preferences ============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove Version Information From Entry Titles"
            summary = "This removes version tags like '(Official)' or '(Yaoi)' from entry titles.\n" +
                "To update existing entries, enable 'Update library manga titles' in advanced settings and refresh manually."
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = REMOVE_TITLE_CUSTOM_PREF
            title = "Custom Regex To Be Removed From Title"
            summary = customRemoveTitle()
            setDefaultValue("")

            val validate = { str: String ->
                runCatching { Regex(str) }
                    .map { true to "" }
                    .getOrElse { false to it.message }
            }

            setOnBindEditTextListener { editText ->
                editText.addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                        override fun afterTextChanged(editable: Editable?) {
                            editable ?: return
                            val text = editable.toString()
                            val valid = validate(text)
                            editText.error = if (!valid.first) valid.second else null
                            editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = editText.error == null
                        }
                    },
                )
            }

            setOnPreferenceChangeListener { _, newValue ->
                val (isValid, message) = validate(newValue as String)
                if (isValid) summary = newValue else Toast.makeText(screen.context, message, Toast.LENGTH_LONG).show()
                isValid
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = IGNORE_GENRE_BLOCKLIST_PREF
            title = "Ignore WebView Genre Blocklist"
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = DEDUPLICATE_CHAPTERS_PREF
            title = "Deduplicate Chapter List"
            summary = "Use the server's deduplicated list within each edition. Chapters from different editions remain separate."
            setDefaultValue(true)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = SOURCE_INCLUDE_PREF
            title = "Chapter Uploaders To Include"
            summary = includeUploadersSummary()
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                summary = includeUploadersSummary(newValue as String)
                true
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = GROUP_BY_SOURCE_PREF
            title = "Group Chapters By Uploader"
            summary = "Sort chapters by uploader, then chapter number."
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = GROUP_BY_EDITION_PREF
            title = "Group Chapters By Edition"
            summary = "Sort chapters by source edition, then chapter number."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun isRemoveTitleVersion(): Boolean = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private fun customRemoveTitle(): String = preferences.getString(REMOVE_TITLE_CUSTOM_PREF, "")!!
    private fun isIgnoreGenreBlocklist(): Boolean = preferences.getBoolean(IGNORE_GENRE_BLOCKLIST_PREF, false)
    private fun isDeduplicateChapters(): Boolean = preferences.getBoolean(DEDUPLICATE_CHAPTERS_PREF, true)
    private fun isGroupBySource(): Boolean = preferences.getBoolean(GROUP_BY_SOURCE_PREF, false)
    private fun isGroupByEdition(): Boolean = preferences.getBoolean(GROUP_BY_EDITION_PREF, false)
    private fun getIncludeSources(): Set<String> = preferences.getString(SOURCE_INCLUDE_PREF, "")!!
        .split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    private fun includeUploadersSummary(value: String = preferences.getString(SOURCE_INCLUDE_PREF, "")!!): String = "Comma-separated uploader names; chapters from other uploaders are hidden. Current: ${value.ifBlank { "(all uploaders)" }}"

    private class DefaultSortFilter(val sort: String) : Filter.Header("")

    private fun mapLangCode(code: String): String = when (code) {
        "pt-BR" -> "pt_br"
        "es-419" -> "es_419"
        "zh-Hant" -> "zh_hk"
        "other" -> "_t"
        else -> code
    }

    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val IGNORE_GENRE_BLOCKLIST_PREF = "IGNORE_GENRE_BLOCKLIST"
        private const val DEDUPLICATE_CHAPTERS_PREF = "DEDUPLICATE_CHAPTERS"
        private const val SOURCE_INCLUDE_PREF = "SOURCE_INCLUDE"
        private const val GROUP_BY_SOURCE_PREF = "GROUP_BY_SOURCE"
        private const val GROUP_BY_EDITION_PREF = "GROUP_BY_EDITION"

        private val idQueryRegex = Regex("^id\\s*:?\\s*([a-zA-Z0-9-_]+)\\s*$", RegexOption.IGNORE_CASE)

        private const val BROWSE_PAGE_SIZE = 48

        private const val PROBE_BATCH = 8

        private const val MEMO_FETCHED_AT = "chaptersFetchedAt" // when we last pulled the chapter lists
        private const val MEMO_LAST_PUBLIC = "lastPublicAt" // title chap_last_public_at at that time
        private const val MEMO_SOURCES_FILTER = "sourcesFilter" // include list at that time
        private const val MEMO_CHAPTER_DATA_VERSION = "chapterDataVersion"
        private const val MEMO_DEDUPLICATE = "chapterDeduplicate"
        private const val CHAPTER_DATA_VERSION = 2

        private val filterValueRegex = Regex("""^[a-z0-9][a-z0-9_]*$""")

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
