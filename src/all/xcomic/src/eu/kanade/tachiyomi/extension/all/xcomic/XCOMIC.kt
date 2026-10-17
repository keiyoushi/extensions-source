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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import org.jsoup.parser.Parser

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
        var chapMin = ""
        var chapMax = ""

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
                is MinChapterFilter -> chapMin = filter.state.trim()
                is MaxChapterFilter -> chapMax = filter.state.trim()
                else -> {}
            }
        }
        // NOTE: no UploadStatusFilter — Title_Browse_Select has no siteStatus.

        val chapMinNum = chapMin.toIntOrNull()
        val chapMaxNum = chapMax.toIntOrNull()

        val chapCount = when {
            chapMinNum != null && chapMaxNum != null -> "${chapMinNum.coerceAtLeast(1)}-${chapMaxNum.coerceAtLeast(1)}"
            chapMinNum != null -> "${chapMinNum.coerceAtLeast(1)}"
            chapMaxNum != null -> "1-${chapMaxNum.coerceAtLeast(1)}"
            else -> null
        }

        val variables = ApiTitleBrowseVariables(
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
            chapCount = chapCount?.takeIf { it.isNotEmpty() },
            ignoreGlobalGenres = isIgnoreGenreBlocklist(),
        )

        val wrapper = ApiTitleBrowseWrapper(variables)
        return coroutineScope {
            val itemsDeferred = async {
                val payload = graphQLBody(query = TITLE_BROWSE_QUERY, variables = wrapper)
                client.post("$baseUrl/query/", payload).parseGraphQLAs<TitleBrowseData>()
            }
            val pagerDeferred = async {
                val payload = graphQLBody(query = TITLE_BROWSE_PAGER_QUERY, variables = wrapper)
                client.post("$baseUrl/query/", payload).parseGraphQLAs<TitleBrowsePagerData>().pager
            }

            MangasPage(parseSearchManga(itemsDeferred.await()), pagerDeferred.await().hasNextPage())
        }
    }

    private fun parseSearchManga(data: TitleBrowseData): List<SManga> = data.items.orEmpty()
        .mapNotNull { it.toSManga(baseUrl, ::cleanTitleIfNeeded) }
        .distinctBy { it.url }

    private fun TitleBrowseNode.toSManga(baseUrl: String, cleanTitle: (String) -> String): SManga? {
        val titleId = id?.takeIf { it.isNotBlank() } ?: return null
        val item = data
        return SManga.create().apply {
            url = titleId
            title = cleanTitle(item?.title.orEmpty()).unescapeHtml().ifBlank { titleId }
            thumbnail_url = (item?.coverLocalUrl ?: item?.coverUrl)?.let {
                if (it.startsWith("http")) it else "$baseUrl$it"
            }
            memo = buildJsonObject { put(MEMO_TITLE_ID, titleId) }
        }
    }

    // ============================== Filters ==============================
    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get("$baseUrl/search")
        val document = response.asJsoup()

        val filterMap = mutableMapOf<String, MutableList<Map<String, String>>>()

        document.select("details.group").forEach { details ->
            val summaryText = details.selectFirst("summary")?.text()?.lowercase() ?: return@forEach
            val category = when {
                "genre" in summaryText -> "genres"
                "type" in summaryText -> "types"
                "demographic" in summaryText -> "demographics"
                "content rating" in summaryText -> "contentRatings"
                else -> null
            }

            if (category != null) {
                val options = (
                    if (category == "genres") {
                        extractSlugFilterOptions(details)
                    } else {
                        extractNamedFilterOptions(details)
                    }
                    ).toMutableList()

                if (category == "genres") {
                    val formatsHeader = details.select("div").firstOrNull { it.ownText() == "Formats" }
                    val formats = formatsHeader?.nextElementSibling()?.let(::extractSlugFilterOptions).orEmpty()
                    if (formats.isNotEmpty()) {
                        filterMap.getOrPut("formats") { mutableListOf() }.addAll(formats)
                        val formatValues = formats.map { it["value"] }.toSet()
                        options.removeAll { it["value"] in formatValues }
                    }
                }

                filterMap.getOrPut(category) { mutableListOf() }.addAll(options)
            }
        }

        val cleanMap = filterMap.mapValues { it.value.distinctBy { v -> v["value"] } }
        validateDynamicFilters(cleanMap)
        return cleanMap.toJsonElement()
    }

    private fun extractNamedFilterOptions(scope: org.jsoup.nodes.Element): List<Map<String, String>> = scope.select("label[data-value]").mapNotNull { label ->
        val value = label.attr("data-value")
        val name = label.selectFirst("span")?.text()?.trim()
        if (value.matches(filterValueRegex) && !name.isNullOrEmpty()) {
            mapOf("name" to name, "value" to value)
        } else {
            null
        }
    }

    private fun extractSlugFilterOptions(scope: org.jsoup.nodes.Element): List<Map<String, String>> = scope.select("div[:]").mapNotNull { div ->
        val value = div.attr(":")
        val name = div.selectFirst("span")?.text()?.trim()
        if (value.matches(filterValueRegex) && !name.isNullOrEmpty()) {
            mapOf("name" to name, "value" to value)
        } else {
            null
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val parsed = data?.parseAs<Map<String, List<Map<String, String>>>>()
        parsed?.let(::validateDynamicFilters)

        fun extractList(key: String): List<Pair<String, String>> = parsed?.get(key)?.mapNotNull { map ->
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
                add(SortFilter())
                if (dynamicContentRatings.isNotEmpty()) add(ContentRatingFilter(options = dynamicContentRatings))
                if (dynamicTypes.isNotEmpty()) add(TypeFilter(options = dynamicTypes))
                add(Filter.Separator())
                if (dynamicDemographics.isNotEmpty()) add(DemographicFilter(options = dynamicDemographics))
                if (dynamicGenres.isNotEmpty()) add(GenreGroupFilter(options = dynamicGenres))
                add(if (dynamicFormats.isNotEmpty()) FormatFilter(dynamicFormats) else FormatFilter())
                add(GenreInModeFilter())
                add(GenreExModeFilter())
                add(Filter.Separator())
                add(OriginalStatusFilter())
                add(OriginalLanguageFilter())
                if (lang == "all") add(TranslationLanguageFilter())
                add(MinChapterFilter())
                add(MaxChapterFilter())
                add(YearFilter())
            },
        )
    }

    private fun validateDynamicFilters(filters: Map<String, List<Map<String, String>>>) {
        val incompleteGroups = DYNAMIC_FILTER_GROUPS.filter { key ->
            val options = filters[key]
            options.isNullOrEmpty() || options.any { option ->
                option["name"].isNullOrBlank() || option["value"]?.matches(filterValueRegex) != true
            }
        }
        if (incompleteGroups.isNotEmpty()) {
            throw Exception("Incomplete XCOMIC filters: ${incompleteGroups.joinToString()}")
        }
    }

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)
        val title = resolveStoredTitleNode(manga.url) ?: error("Title not found: ${manga.url}")
        val editions = resolveTargetComics(title)
        val deduplicate = isDeduplicateChapters()
        val editionFilter = includedEditionLabels().sorted().joinToString(",")
        val editionFingerprint = editions.fingerprint()
        val chaptersCurrent = isChapterCacheCurrent(manga, title.chapLastPublicAt, editions, editionFingerprint, editionFilter, deduplicate)

        val chapterList = when {
            !fetchChapters -> chapters
            chaptersCurrent -> postProcessChapters(disambiguateEditionLabels(chapters))
            editions.sources.isEmpty() -> emptyList()
            else -> fetchAllChapters(editions.sources, deduplicate)
        }

        val details = if (fetchDetails) {
            getMangaDetails(manga, title, editions, chapterList.mapNotNull { it.uploader() }.distinct())
        } else {
            manga
        }

        details.url = manga.url
        val chapterMemo = if (fetchChapters && !chaptersCurrent && editions.sources.isNotEmpty()) {
            buildChapterMemo(manga.memo, title.chapLastPublicAt, editionFingerprint, editionFilter, deduplicate)
        } else {
            manga.memo
        }
        details.memo = buildTitleMemo(chapterMemo, title)

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchTitleNode(id: String): TitleNodeData? {
        val payload = graphQLBody(query = TITLE_NODE_QUERY, variables = ApiTitleNodeVariables(id))
        return client.post("$baseUrl/query/", payload).parseGraphQLAs<TitleNodeEnvelope>().response?.data
    }

    private suspend fun fetchComicNode(id: String): ComicNode? {
        val payload = graphQLBody(query = COMIC_NODE_QUERY, variables = ApiComicNodeVariables(id))
        return client.post("$baseUrl/query/", payload).parseGraphQLAs<ComicNodeData>().response.data
    }

    private suspend fun fetchComicProbe(id: String): ComicProbeData? {
        val payload = graphQLBody(query = COMIC_PROBE_QUERY, variables = ApiComicNodeVariables(id))
        return client.post("$baseUrl/query/", payload).parseGraphQLAs<ComicProbeEnvelope>().response?.data
    }

    private suspend fun fetchResolvedTitleNode(id: String): TitleNodeData? {
        val title = fetchTitleNode(id) ?: return null
        val mergedId = title.mergedTo?.takeIf { title.isMerged == true && it.isNotBlank() && it != id }
        return mergedId?.let { fetchTitleNode(it) } ?: title
    }

    private suspend fun resolveStoredTitleNode(storedUrl: String): TitleNodeData? {
        val ids = storedUrl.split(':', limit = 2).filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return null

        fetchResolvedTitleNode(ids.first())?.let { return it }

        for (comicId in ids) {
            val comic = fetchComicNode(comicId) ?: continue
            val parentTitle = comic.titleNode ?: continue
            val parentId = parentTitle.id?.takeIf { it.isNotBlank() } ?: continue
            return fetchResolvedTitleNode(parentId) ?: parentTitle
        }
        return null
    }

    private suspend fun resolveTargetComics(title: TitleNodeData): TitleEditions {
        val comicIds = title.comicIds.orEmpty().filter { it.isNotBlank() }.distinct()
        val probeResults = coroutineScope {
            comicIds.chunked(COMIC_PROBES_PER_TITLE).flatMap { batch ->
                batch.map { comicId -> async { comicId to fetchComicProbe(comicId) } }.awaitAll()
            }
        }
        val expectedLanguage = if (lang == "all") null else mapLangCode(lang)
        val sources = probeResults
            .mapNotNull { (comicId, probe) -> probe?.let { comicId to it } }
            .filter { (_, probe) -> probe.isLive() && (expectedLanguage == null || probe.translatedLanguage == expectedLanguage) }
            .map { (comicId, probe) ->
                ChapterEdition(
                    comicId = comicId,
                    label = probe.subName.normalizeEditionLabel(),
                    lastPublicAt = probe.chapterUpTo?.data?.datePublic,
                    chapterCount = probe.chapsNormal,
                    translatedLanguage = probe.translatedLanguage,
                )
            }
            .sortedBy { it.comicId }

        return TitleEditions(sources)
    }

    private fun isChapterCacheCurrent(
        manga: SManga,
        titleLastPublicAt: Long?,
        editions: TitleEditions,
        editionFingerprint: String,
        editionFilter: String,
        deduplicate: Boolean,
    ): Boolean {
        val titleLastPublic = titleLastPublicAt?.takeIf { it > 0L } ?: return false
        val fetchedAt = manga.memo[MEMO_FETCHED_AT]?.string?.toLongOrNull()?.takeIf { it > 0L } ?: return false
        val previousTitleLastPublic = manga.memo[MEMO_LAST_PUBLIC]?.string?.toLongOrNull()?.takeIf { it > 0L } ?: return false
        if (!editions.hasUsableFreshness || editions.sources.any { edition ->
                edition.lastPublicAt?.takeIf { it > 0L }?.let { it > fetchedAt } == true
            }
        ) {
            return false
        }

        return previousTitleLastPublic == titleLastPublic &&
            manga.memo[MEMO_EDITION_FINGERPRINT]?.string == editionFingerprint &&
            manga.memo[MEMO_CHAPTER_DATA_VERSION]?.string?.toIntOrNull() == CHAPTER_DATA_VERSION &&
            manga.memo[MEMO_DEDUPLICATE]?.string == deduplicate.toString() &&
            manga.memo[MEMO_SOURCES_FILTER]?.string.orEmpty() == editionFilter
    }

    private fun buildChapterMemo(
        original: JsonElement,
        titleLastPublicAt: Long?,
        editionFingerprint: String,
        editionFilter: String,
        deduplicate: Boolean,
    ) = buildJsonObject {
        original.jsonObject.forEach { (key, value) -> put(key, value) }
        put(MEMO_FETCHED_AT, System.currentTimeMillis().toString())
        titleLastPublicAt?.takeIf { it > 0L }?.let { put(MEMO_LAST_PUBLIC, it.toString()) }
        put(MEMO_EDITION_FINGERPRINT, editionFingerprint)
        put(MEMO_CHAPTER_DATA_VERSION, CHAPTER_DATA_VERSION.toString())
        put(MEMO_DEDUPLICATE, deduplicate.toString())
        put(MEMO_SOURCES_FILTER, editionFilter)
    }

    private fun buildTitleMemo(original: JsonElement, title: TitleNodeData) = buildJsonObject {
        original.jsonObject.forEach { (key, value) -> put(key, value) }
        title.id?.takeIf { it.isNotBlank() }?.let { put(MEMO_TITLE_ID, it) }
    }

    private suspend fun getMangaDetails(
        manga: SManga,
        title: TitleNodeData,
        editions: TitleEditions,
        uploaders: List<String>,
    ): SManga {
        val comicId = editions.bestComicId ?: title.comicIds.orEmpty().firstOrNull() ?: return manga
        val comic = fetchComicNode(comicId) ?: return manga
        return comic.toSManga(baseUrl, ::cleanTitleIfNeeded, title, uploaders).apply {
            url = manga.url
            memo = buildJsonObject {}
        }
    }

    private class ChapterEdition(
        val comicId: String,
        val label: String?,
        val lastPublicAt: Long?,
        val chapterCount: Int?,
        val translatedLanguage: String?,
    )

    private class TitleEditions(val sources: List<ChapterEdition>) {
        val bestComicId: String?
            get() = sources.maxByOrNull { it.chapterCount ?: Int.MIN_VALUE }?.comicId

        val hasUsableFreshness: Boolean
            get() = sources.isNotEmpty() && sources.all { edition ->
                (edition.lastPublicAt ?: 0L) > 0L || edition.chapterCount == 0
            }

        fun fingerprint(): String = buildJsonArray {
            sources.sortedBy { it.comicId }.forEach { edition ->
                add(
                    buildJsonObject {
                        put("comicId", edition.comicId)
                        put("label", edition.label.orEmpty())
                        put("lastPublicAt", edition.lastPublicAt ?: 0L)
                        put("chapterCount", edition.chapterCount?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("translatedLanguage", edition.translatedLanguage.orEmpty())
                    },
                )
            }
        }.toString()
    }

    private suspend fun resolveIdLookup(id: String): SManga? {
        val title = resolveStoredTitleNode(id) ?: return null
        val titleId = title.id?.takeIf { it.isNotBlank() } ?: return null
        val manga = title.toBrowseNode().toSManga(baseUrl, ::cleanTitleIfNeeded) ?: return null
        manga.url = titleId
        return getMangaDetails(manga, title, resolveTargetComics(title), emptyList()).apply {
            url = titleId
            memo = buildTitleMemo(memo, title)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val seg = url.pathSegments
        val id = seg.takeIf { it.size >= 2 }?.get(1)?.substringBefore("-") ?: return null
        return when (seg[0]) {
            "title" -> resolveIdLookup(id)
            "source", "comic" -> resolveIdLookup(id)
            else -> null
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val titleId = manga.memo[MEMO_TITLE_ID]?.string?.takeIf { it.isNotBlank() } ?: manga.url.substringBefore(':')
        return "$baseUrl/title/$titleId"
    }

    // ============================= Chapters ==============================
    private suspend fun fetchAllChapters(sources: List<ChapterEdition>, deduplicate: Boolean): List<SChapter> = coroutineScope {
        val chapters = sources.chunked(COMIC_PROBES_PER_TITLE).flatMap { batch ->
            batch.map { source ->
                async { fetchChapterList(source.comicId, deduplicate, source.label) }
            }.awaitAll().flatten()
        }
        postProcessChapters(disambiguateEditionLabels(chapters))
    }

    private fun disambiguateEditionLabels(chapters: List<SChapter>): List<SChapter> {
        val chapterLabels = chapters.mapNotNull { chapter ->
            val label = chapter.scanlator.normalizeEditionLabel() ?: chapter.uploader().normalizeEditionLabel()
            val comicId = chapter.comicId()
            if (label == null || comicId == null) null else Triple(chapter, label, comicId)
        }
        val collidingEditions = chapterLabels
            .groupBy { (_, label, _) -> label.lowercase() }
            .mapValues { (_, entries) -> entries.map { it.third }.distinct().takeIf { it.size > 1 }.orEmpty() }
            .filterValues { it.isNotEmpty() }

        return chapters.onEach { chapter ->
            val label = chapter.scanlator.normalizeEditionLabel() ?: chapter.uploader().normalizeEditionLabel()
            val comicId = chapter.comicId()
            chapter.scanlator = when {
                label == null -> null
                comicId != null && comicId in collidingEditions[label.lowercase()].orEmpty() -> "$label [$comicId]"
                else -> label
            }
        }
    }

    private suspend fun fetchChapterList(comicId: String, deduplicate: Boolean, editionLabel: String?): List<SChapter> = coroutineScope {
        val pageSize = if (deduplicate) 1000 else 100

        val firstPage = fetchChapterListPage(comicId, 1, deduplicate, pageSize, editionLabel)
        val allChapters = firstPage.chapters.toMutableList()
        val totalItems = firstPage.total

        if (firstPage.hasNextPage) {
            if (totalItems == null || totalItems <= pageSize) {
                error("Chapter pagination is incomplete for comic $comicId")
            }
            val totalPages = (totalItems + (pageSize - 1)) / pageSize

            (2..totalPages).chunked(3).forEach { batch ->
                val pages = batch.map { pageNum ->
                    async {
                        fetchChapterListPage(comicId, pageNum, deduplicate, pageSize, editionLabel)
                    }
                }.awaitAll()

                pages.forEachIndexed { index, page ->
                    val pageNum = batch[index]
                    if (page.hasNextPage != (pageNum < totalPages) || page.total != totalItems) {
                        error("Chapter pagination changed while fetching comic $comicId")
                    }
                    allChapters.addAll(page.chapters)
                }
            }
        } else if (totalItems != null && totalItems > pageSize) {
            error("Chapter pagination ended early for comic $comicId")
        }

        if (totalItems != null && allChapters.size != totalItems) {
            error("Expected $totalItems chapters for comic $comicId, received ${allChapters.size}")
        }

        allChapters
    }

    private fun postProcessChapters(chapters: List<SChapter>): List<SChapter> {
        val includedEditions = includedEditionLabels()
        val filtered = if (includedEditions.isEmpty()) {
            chapters
        } else {
            chapters.filter { chapter ->
                val label = chapter.scanlator.normalizeEditionLabel()?.lowercase() ?: return@filter false
                label in includedEditions || includedEditions.any { base -> label.startsWith("$base [") }
            }
        }

        return if (isGroupByEdition()) {
            filtered.sortedWith(compareBy<SChapter> { it.scanlator?.lowercase().orEmpty() }.thenByDescending { it.chapter_number })
        } else {
            filtered.sortedByDescending { it.chapter_number }
        }
    }

    private suspend fun fetchChapterListPage(
        comicId: String,
        page: Int,
        deduplicate: Boolean,
        pageSize: Int,
        editionLabel: String?,
    ): ChapterListPage {
        val select = ApiChapterListSelect(
            comicId = comicId,
            page = page,
            size = pageSize,
        )

        val query = if (deduplicate) CHAPTER_UNIQ_LIST_QUERY else CHAPTER_LIST_QUERY
        val payload = graphQLBody(query = query, variables = ApiChapterListWrapper(select))
        val response = client.post("$baseUrl/query/", payload)

        val data = if (deduplicate) {
            response.parseGraphQLAs<ChapterListUniqData>().response
        } else {
            response.parseGraphQLAs<ChapterListData>().response
        }

        return ChapterListPage(
            chapters = data.items.map { it.data.toSChapter(comicId, editionLabel) },
            total = data.paging.total,
            hasNextPage = data.paging.hasNextPage(),
        )
    }

    private data class ChapterListPage(
        val chapters: List<SChapter>,
        val total: Int?,
        val hasNextPage: Boolean,
    )

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = getChapterId(chapter.url)

        val payload = graphQLBody(query = CHAPTER_PAGES_QUERY, variables = ApiChapterNodeVariables(chapterId))
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

    private fun getChapterId(url: String): String = url

    // ============================ Title node mapper ==========================
    private fun TitleNodeData.toBrowseNode(): TitleBrowseNode = TitleBrowseNode(
        id = id,
        data = TitleBrowseItem(
            title = title,
            nativeTitle = nativeTitle,
            romanizedTitle = romanizedTitle,
            originalLanguage = originalLanguage,
            translatedLanguages = translatedLanguages,
            type = type,
            chapLastPublicAt = chapLastPublicAt,
            coverLocalUrl = coverLocalUrl,
            coverUrl = coverUrl,
            comicIds = comicIds,
        ),
    )

    // ============================ Title cleaning ============================
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
            summary = "Use a deduplicated chapter list from server side.\nNote: May hide duplicate uploads from the same edition."
            setDefaultValue(true)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = SOURCE_INCLUDE_PREF
            title = "Chapter Editions To Include"
            summary = includeEditionsSummary()
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                summary = includeEditionsSummary(newValue as String)
                true
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = GROUP_BY_SOURCE_PREF
            title = "Group Chapters By Edition"
            summary = "Sort chapters by edition label, then chapter number."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun isRemoveTitleVersion(): Boolean = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private fun customRemoveTitle(): String = preferences.getString(REMOVE_TITLE_CUSTOM_PREF, "")!!
    private fun isIgnoreGenreBlocklist(): Boolean = preferences.getBoolean(IGNORE_GENRE_BLOCKLIST_PREF, false)
    private fun isDeduplicateChapters(): Boolean = preferences.getBoolean(DEDUPLICATE_CHAPTERS_PREF, true)
    private fun isGroupByEdition(): Boolean = preferences.getBoolean(GROUP_BY_SOURCE_PREF, false)
    private fun includedEditionLabels(): Set<String> = preferences.getString(SOURCE_INCLUDE_PREF, "")!!
        .split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    private fun includeEditionsSummary(value: String = preferences.getString(SOURCE_INCLUDE_PREF, "")!!): String = "Comma-separated edition labels; a base label includes matching editions, while a suffixed label selects one. Current: ${value.ifBlank { "(all editions)" }}"

    // ========================= Helpers =========================
    private fun String.unescapeHtml(): String = Parser.unescapeEntities(this, false)

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

        private val idQueryRegex = Regex("^id\\s*:?\\s*([a-zA-Z0-9_-]+(?::[a-zA-Z0-9_-]+)?)\\s*$", RegexOption.IGNORE_CASE)

        // Maximum is 48
        private const val BROWSE_PAGE_SIZE = 12

        private const val COMIC_PROBES_PER_TITLE = 5

        private val filterValueRegex = Regex("""^[a-z0-9][a-z0-9_]*$""")
        private val DYNAMIC_FILTER_GROUPS = listOf("contentRatings", "types", "demographics", "genres", "formats")

        private const val MEMO_TITLE_ID = "titleId"
        private const val MEMO_FETCHED_AT = "chaptersFetchedAt" // when we last pulled the list
        private const val MEMO_LAST_PUBLIC = "lastPublicAt" // title.chapLastPublicAt at that time
        private const val MEMO_EDITION_FINGERPRINT = "chapterEditions"
        private const val MEMO_CHAPTER_DATA_VERSION = "chapterDataVersion"
        private const val MEMO_DEDUPLICATE = "chapterDeduplicate"
        private const val MEMO_SOURCES_FILTER = "sourcesFilter"
        private const val CHAPTER_DATA_VERSION = 1

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
