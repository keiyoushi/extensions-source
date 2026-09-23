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
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import org.jsoup.parser.Parser
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class XCOMIC :
    KeiSource(),
    ConfigurableSource {

    private val probeCache = ConcurrentHashMap<String, ComicProbeData>()

    private val titleFreshness = ConcurrentHashMap<String, Long>()

    private val preferences by getPreferencesLazy()

    // ========================= Popular & Latest ==========================
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(DefaultSortFilter("field_score")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(DefaultSortFilter("field_update")))

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val idMatch = idQueryRegex.matchEntire(query.trim())
        if (idMatch != null) {
            val id = idMatch.groupValues[1].substringBefore("-")
            val extLang = if (lang == "all") null else mapLangCode(lang)

            val node = fetchTitleNode(id)
            if (node != null) {
                val browseNode = node.toBrowseNode()
                val rows = flattenTitle(browseNode, extLang, forceFresh = true)
                if (rows.isNotEmpty()) {
                    val mangas = rows.map { (tid, cid, p) ->
                        p.toBrowseSManga(baseUrl, tid, cid, browseNode, extLang, ::cleanTitleIfNeeded)
                    }
                    return MangasPage(mangas, false)
                }
            }
            return runCatching { MangasPage(listOf(legacyComicDetails(id)), false) }
                .getOrElse { MangasPage(emptyList(), false) }
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

    /**
     * Flatten: title page → concurrent per-title probing (TITLES_IN_FLIGHT
     * titles at a time, COMIC_PROBES_PER_TITLE probes each) → one row per
     * live source in the extension language.
     */
    private suspend fun parseSearchManga(data: TitleBrowseData): List<SManga> {
        val titles = data.items.orEmpty()
        if (titles.isEmpty()) return emptyList()
        val extLang = if (lang == "all") null else mapLangCode(lang)

        val flattened = coroutineScope {
            titles.chunked(TITLES_IN_FLIGHT).flatMap { batch ->
                batch.map { t -> async { flattenTitle(t, extLang) } }.awaitAll()
            }
        }.flatten()

        val mangas = flattened.map { (titleId, cid, p) ->
            val t = titles.first { it.id == titleId }
            p.toBrowseSManga(baseUrl, titleId, cid, t, extLang, ::cleanTitleIfNeeded)
        }

        return mangas
    }

    private suspend fun flattenTitle(
        t: TitleBrowseNode,
        extLang: String?,
        forceFresh: Boolean = false,
    ): List<Triple<String, String, ComicProbeData>> {
        val titleId = t.id?.takeIf { it.isNotBlank() } ?: return emptyList()
        val ids = t.data?.comicIds.orEmpty().filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyList()

        val nowPublic = t.data?.chapLastPublicAt ?: 0L
        val unchanged = !forceFresh && nowPublic in 1..(titleFreshness[titleId] ?: 0L)

        if (unchanged && ids.all { probeCache.containsKey(it) }) {
            return ids.mapNotNull { cid ->
                probeCache[cid]?.takeIf {
                    it.isLive() && (extLang == null || it.translatedLanguage == extLang)
                }?.let { Triple(titleId, cid, it) }
            }.sortedByDescending { it.third.chapsNormal ?: 0 }
        }

        val probes = mutableMapOf<String, ComicProbeData>()
        coroutineScope {
            ids.chunked(COMIC_PROBES_PER_TITLE).flatMap { chunk ->
                chunk.map { cid ->
                    async { fetchComicProbe(cid)?.let { probes[cid] = it } }
                }.awaitAll()
            }
        }
        if (nowPublic > 0) titleFreshness[titleId] = nowPublic
        return probes.entries
            .filter { it.value.isLive() && (extLang == null || it.value.translatedLanguage == extLang) }
            .map { (cid, p) -> Triple(titleId, cid, p) }
            .sortedByDescending { it.third.chapsNormal ?: 0 }
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
                val options = extractFilterOptions(details).toMutableList()

                if (category == "genres") {
                    val formatsHeader = details.select("div").firstOrNull { it.ownText() == "Formats" }
                    val formats = formatsHeader?.nextElementSibling()?.let(::extractFilterOptions).orEmpty()
                    if (formats.isNotEmpty()) {
                        filterMap.getOrPut("formats") { mutableListOf() }.addAll(formats)
                        val formatValues = formats.map { it["value"] }.toSet()
                        options.removeAll { it["value"] in formatValues }
                    }
                }

                filterMap.getOrPut(category) { mutableListOf() }.addAll(options)
            }
        }

        if (filterMap["genres"].isNullOrEmpty() && filterMap["types"].isNullOrEmpty()) {
            throw Exception("Failed to fetch filters dynamically")
        }

        val cleanMap = filterMap.mapValues { it.value.distinctBy { v -> v["value"] } }
        return cleanMap.toJsonElement()
    }

    private fun extractFilterOptions(scope: org.jsoup.nodes.Element): List<Map<String, String>> = scope.select("div[:]").mapNotNull { div ->
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

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)

        val edition = resolveManga(manga)
        val editionLabel = edition.comic.subName.normalizeEditionLabel()
        val deduplicate = isDeduplicateChapters()
        val editionFilter = includedEditionLabels().sorted().joinToString(",")
        val chaptersCurrent = isChapterCacheCurrent(manga, edition, editionLabel, editionFilter, deduplicate)

        val chapterList = if (!fetchChapters) {
            chapters
        } else {
            val cachedOrFetched = if (chaptersCurrent) {
                chapters
            } else {
                fetchChapterList(edition.comicId, deduplicate, editionLabel)
            }
            postProcessChapters(cachedOrFetched)
        }

        val details = if (fetchDetails) {
            getMangaDetails(manga, edition, chapterList.mapNotNull { it.uploader() }.distinct())
        } else {
            manga
        }

        val fetchedAt = if (fetchChapters && !chaptersCurrent) System.currentTimeMillis() else null
        details.memo = buildMangaMemo(manga, details, edition, editionLabel, fetchDetails, fetchedAt, editionFilter, deduplicate)

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchTitleNode(id: String): TitleNodeData? = runCatching {
        val payload = graphQLBody(query = TITLE_NODE_QUERY, variables = ApiTitleNodeVariables(id))
        client.post("$baseUrl/query/", payload).parseGraphQLAs<TitleNodeEnvelope>().response?.data
    }.getOrNull()

    private suspend fun fetchComicNode(id: String): ComicNode? = runCatching {
        val payload = graphQLBody(query = COMIC_NODE_QUERY, variables = ApiComicNodeVariables(id))
        client.post("$baseUrl/query/", payload).parseGraphQLAs<ComicNodeData>().response.data
    }.getOrNull()

    private suspend fun fetchComicProbe(id: String): ComicProbeData? {
        probeCache[id]?.let { return it }
        val fetched = runCatching {
            val payload = graphQLBody(query = COMIC_PROBE_QUERY, variables = ApiComicNodeVariables(id))
            client.post("$baseUrl/query/", payload).parseGraphQLAs<ComicProbeEnvelope>().response?.data
        }.getOrNull()
        if (fetched != null) probeCache[id] = fetched
        return fetched
    }

    private suspend fun resolveManga(manga: SManga): MangaEdition {
        val extLang = if (lang == "all") null else mapLangCode(lang)
        val (titleId, pinned) = splitMangaUrl(manga.url)

        val title = fetchTitleNode(titleId)
        if (title == null) {
            val comicId = pinned ?: manga.url
            val comic = fetchComicNode(comicId) ?: error("Comic not found: $comicId")
            return MangaEdition(comicId, null, true, comic.titleNode, comic)
        }

        val resolvedTitle = title.mergedTo
            ?.takeIf { title.isMerged == true && it.isNotBlank() && it != titleId }
            ?.let { fetchTitleNode(it) }
            ?: title
        val comicIds = resolvedTitle.comicIds.orEmpty().filter { it.isNotBlank() }
        val selectedId = pinned ?: manga.memo[MEMO_SOURCE_ID]?.string?.takeIf { it in comicIds }

        if (selectedId != null) {
            val comic = fetchComicNode(selectedId) ?: error("Edition not found: $selectedId")
            return MangaEdition(selectedId, titleId, false, resolvedTitle, comic)
        }

        val (comicId, comic) = pickComic(comicIds, extLang)
            ?: error("Failed to load '$lang' uploads for this source")
        return MangaEdition(comicId, titleId, false, resolvedTitle, comic)
    }

    private fun getMangaDetails(manga: SManga, edition: MangaEdition, uploaders: List<String>): SManga = edition.comic.toSManga(baseUrl, ::cleanTitleIfNeeded, edition.title, uploaders).apply {
        url = manga.url
        val label = edition.comic.subName?.takeIf { it.isNotBlank() }
            ?: manga.memo[MEMO_LABEL]?.string?.takeIf { manga.memo[MEMO_SOURCE_ID]?.string == edition.comicId }
        label?.let {
            title = "$title · ${it.unescapeHtml()}"
        }
    }

    private fun isChapterCacheCurrent(
        manga: SManga,
        edition: MangaEdition,
        editionLabel: String?,
        editionFilter: String,
        deduplicate: Boolean,
    ): Boolean {
        val titleLastPublic = edition.titleLastPublicAt.takeIf { it > 0L } ?: return false
        val editionLastPublic = edition.editionLastPublicAt.takeIf { it > 0L } ?: return false
        val fetchedAt = manga.memo[MEMO_FETCHED_AT]?.string?.toLongOrNull()?.takeIf { it > 0L } ?: return false
        val previousTitleLastPublic = manga.memo[MEMO_LAST_PUBLIC]?.string?.toLongOrNull() ?: return false
        val previousEditionLastPublic = manga.memo[MEMO_EDITION_LAST_PUBLIC]?.string?.toLongOrNull() ?: return false

        return previousTitleLastPublic == titleLastPublic &&
            previousEditionLastPublic == editionLastPublic &&
            editionLastPublic <= fetchedAt &&
            manga.memo[MEMO_CHAPTER_EDITION_ID]?.string == edition.comicId &&
            manga.memo[MEMO_CHAPTER_EDITION_LABEL]?.string == editionLabel.orEmpty() &&
            manga.memo[MEMO_CHAPTER_DATA_VERSION]?.string?.toIntOrNull() == CHAPTER_DATA_VERSION &&
            manga.memo[MEMO_DEDUPLICATE]?.string == deduplicate.toString() &&
            manga.memo[MEMO_SOURCES_FILTER]?.string == editionFilter
    }

    private fun buildMangaMemo(
        original: SManga,
        updated: SManga,
        edition: MangaEdition,
        editionLabel: String?,
        refreshDetails: Boolean,
        chaptersFetchedAt: Long?,
        editionFilter: String,
        deduplicate: Boolean,
    ) = buildJsonObject {
        original.memo.forEach { (key, value) ->
            if (key != "urlPath" && key != MEMO_SOURCE_ID && key != MEMO_LABEL && key != MEMO_TITLE_ID) put(key, value)
        }
        val urlPath = if (refreshDetails) {
            updated.memo["urlPath"]
        } else {
            original.memo["urlPath"]?.takeIf { original.memo[MEMO_SOURCE_ID]?.string == edition.comicId }
        }
        urlPath?.let { put("urlPath", it) }
        put(MEMO_SOURCE_ID, edition.comicId)
        if (!edition.isLegacy) put(MEMO_TITLE_ID, edition.titleId ?: original.url.substringBefore(':'))
        val label = edition.comic.subName?.takeIf { it.isNotBlank() }
            ?: original.memo[MEMO_LABEL]?.string?.takeIf { original.memo[MEMO_SOURCE_ID]?.string == edition.comicId }
        label?.let { put(MEMO_LABEL, it) }

        chaptersFetchedAt?.let {
            put(MEMO_FETCHED_AT, it.toString())
            put(MEMO_LAST_PUBLIC, edition.titleLastPublicAt.toString())
            put(MEMO_EDITION_LAST_PUBLIC, edition.editionLastPublicAt.toString())
            put(MEMO_CHAPTER_EDITION_ID, edition.comicId)
            put(MEMO_CHAPTER_EDITION_LABEL, editionLabel.orEmpty())
            put(MEMO_CHAPTER_DATA_VERSION, CHAPTER_DATA_VERSION.toString())
            put(MEMO_DEDUPLICATE, deduplicate.toString())
            put(MEMO_SOURCES_FILTER, editionFilter)
        }
    }

    private class MangaEdition(
        val comicId: String,
        val titleId: String?,
        val isLegacy: Boolean,
        val title: TitleNodeData?,
        val comic: ComicNode,
    ) {
        val titleLastPublicAt: Long
            get() = title?.chapLastPublicAt ?: 0L
        val editionLastPublicAt: Long
            get() = comic.chapterUpTo?.data?.datePublic ?: 0L
    }

    /** Probes comic_ids and fetches a full node for the selected edition. */
    private suspend fun pickComic(ids: List<String>, extLang: String?): Pair<String, ComicNode>? {
        if (ids.isEmpty()) return null
        val probes = coroutineScope {
            ids.chunked(COMIC_PROBES_PER_TITLE).flatMap { chunk ->
                chunk.map { cid -> async { fetchComicProbe(cid)?.let { cid to it } } }.awaitAll()
            }
        }.filterNotNull().filter { it.second.isLive() }
        val selectedId = probes.filter { extLang == null || it.second.translatedLanguage == extLang }
            .maxByOrNull { it.second.chapsNormal ?: 0 }
            ?.first
            ?: probes.firstOrNull()?.first?.takeIf { extLang == null }
            ?: return null
        val comic = fetchComicNode(selectedId) ?: return null
        return selectedId to comic
    }

    private suspend fun legacyComicDetails(id: String): SManga {
        val comic = fetchComicNode(id) ?: error("Comic not found: $id")
        val m = comic.toSManga(baseUrl, ::cleanTitleIfNeeded)
        comic.subName?.takeIf { it.isNotBlank() }?.let {
            m.title = "${m.title} · ${it.unescapeHtml()}"
        }
        return m
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val seg = url.pathSegments
        val id = seg.takeIf { it.size >= 2 }?.get(1)?.substringBefore("-") ?: return null
        return when (seg[0]) {
            "title" -> runCatching {
                val manga = SManga.create().apply { this.url = id }
                val edition = resolveManga(manga)
                getMangaDetails(manga, edition, emptyList()).apply {
                    memo = buildMangaMemo(
                        manga,
                        this,
                        edition,
                        edition.comic.subName.normalizeEditionLabel(),
                        true,
                        null,
                        "",
                        isDeduplicateChapters(),
                    )
                }
            }.getOrNull()
            "source" -> runCatching { legacyComicDetails(id) }.getOrNull()
            else -> null
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        manga.memo["urlPath"]?.string?.let { return "$baseUrl$it" }
        return if (':' in manga.url || manga.memo[MEMO_TITLE_ID]?.string != null) {
            "$baseUrl/title/${manga.url.substringBefore(':')}"
        } else {
            "$baseUrl/source/${manga.url}"
        }
    }

    // ============================= Chapters ==============================
    private suspend fun fetchChapterList(comicId: String, deduplicate: Boolean, editionLabel: String?): List<SChapter> = coroutineScope {
        val pageSize = if (deduplicate) 1000 else 100

        val firstPage = fetchChapterListPage(comicId, 1, deduplicate, pageSize, editionLabel)
        val allChapters = firstPage.chapters.toMutableList()
        val totalItems = firstPage.total ?: 0

        if (totalItems > pageSize && firstPage.hasNextPage) {
            val totalPages = (totalItems + (pageSize - 1)) / pageSize

            (2..totalPages).chunked(3).forEach { batch ->
                val deferredPages = batch.map { pageNum ->
                    async {
                        fetchChapterListPage(comicId, pageNum, deduplicate, pageSize, editionLabel).chapters
                    }
                }
                allChapters.addAll(deferredPages.awaitAll().flatten())
            }
        }

        allChapters
    }

    private fun postProcessChapters(chapters: List<SChapter>): List<SChapter> {
        val includedEditions = includedEditionLabels()
        val filtered = if (includedEditions.isEmpty()) {
            chapters
        } else {
            chapters.filter { it.scanlator?.lowercase() in includedEditions }
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

    // ==================== URL splitting + browse row mapper ====================
    private fun splitMangaUrl(url: String): Pair<String, String?> {
        val i = url.indexOf(':')
        return if (i < 0) url to null else url.substring(0, i) to url.substring(i + 1)
    }

    /**
     * One browse row per source: "Title · <subName> · N ch" (+ [lang] when
     * language is "all"). Label = subName only.
     */
    private fun ComicProbeData.toBrowseSManga(
        baseUrl: String,
        titleId: String,
        comicId: String,
        t: TitleBrowseNode,
        extLang: String?,
        cleanTitle: (String) -> String,
    ): SManga = SManga.create().apply {
        url = "$titleId:$comicId"
        val displayTitle = cleanTitle(t.data?.title.orEmpty()).ifBlank { titleId }
        title = buildString {
            append(displayTitle)
            subName?.takeIf { it.isNotBlank() }?.let { append(" · ", it.unescapeHtml()) }
            if (extLang == null) translatedLanguage?.let { append(" [", langDisplayName(it), "]") }
        }

        memo = buildJsonObject {
            put("titleId", titleId)
            put("sourceId", comicId)
            put("label", (subName ?: ""))
            put("tlang", (translatedLanguage ?: ""))
            put("lastPublicAt", "0")
        }

        thumbnail_url = (t.data?.coverLocalUrl ?: t.data?.coverUrl ?: urlCover)
            ?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
    }

    /** Converts a title node into the browse-node shape flattenTitle consumes. */
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
            summary = "Use a deduplicated chapter list from server side.\nNote: May hide other scanlator uploads."
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

    private fun includeEditionsSummary(value: String = preferences.getString(SOURCE_INCLUDE_PREF, "")!!): String = "Comma-separated edition labels; chapters from other editions are hidden. Current: ${value.ifBlank { "(all editions)" }}"

    // ========================= Helpers =========================
    private fun langDisplayName(code: String): String = languages.firstOrNull { it.second == code }?.first ?: code.uppercase()

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
        private const val MEMO_SOURCE_ID = "sourceId"
        private const val MEMO_LABEL = "label"
        private const val MEMO_TITLE_ID = "titleId"

        private val idQueryRegex = Regex("^id\\s*:?\\s*([a-zA-Z0-9-_]+)\\s*$", RegexOption.IGNORE_CASE)

        // Maximum is 48
        private const val BROWSE_PAGE_SIZE = 12

        // Fan-out shape: 3 titles concurrently, 5 comic probes each
        private const val TITLES_IN_FLIGHT = 3
        private const val COMIC_PROBES_PER_TITLE = 5

        private val filterValueRegex = Regex("""^[a-z0-9][a-z0-9_]*$""")

        private const val MEMO_FETCHED_AT = "chaptersFetchedAt" // when we last pulled the list
        private const val MEMO_LAST_PUBLIC = "lastPublicAt" // title.chapLastPublicAt at that time
        private const val MEMO_EDITION_LAST_PUBLIC = "editionLastPublicAt"
        private const val MEMO_CHAPTER_EDITION_ID = "chapterEditionId"
        private const val MEMO_CHAPTER_EDITION_LABEL = "chapterEditionLabel"
        private const val MEMO_CHAPTER_DATA_VERSION = "chapterDataVersion"
        private const val MEMO_DEDUPLICATE = "chapterDeduplicate"
        private const val MEMO_SOURCES_FILTER = "sourcesFilter"
        private const val CHAPTER_DATA_VERSION = 3

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
