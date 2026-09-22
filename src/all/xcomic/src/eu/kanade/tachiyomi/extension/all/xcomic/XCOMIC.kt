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
            val comicNode = fetchComicNode(id) ?: throw Exception("Source id '$id' not found")
            val manga = comicNode.titleNode?.toSManga(baseUrl, ::cleanTitleIfNeeded)
                ?: throw Exception("Source id '$id' has no title")
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
                is OriginalStatusFilter -> origStatus = filter.selected.takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
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
            val details = if (fetchDetails) {
                legacyComic.titleNode?.toSManga(baseUrl, ::cleanTitleIfNeeded, url = manga.url, comic = legacyComic) ?: manga
            } else {
                manga
            }
            val chapterList = if (fetchChapters) fetchAllChapters(listOf(manga.url)) else chapters
            return SMangaUpdate(details, chapterList)
        }

        val sources = fetchTitleSources(manga.url)
        if (sources.isEmpty()) throw Exception("No sources found for this title")

        val targets = if (fetchChapters) resolveTargetComics(sources) else null

        val details = if (fetchDetails) {
            val comicNode = targets?.probeComic
                ?: fetchComicNode(targets?.comicIds?.firstOrNull() ?: sources.first().comicId)
            comicNode?.titleNode?.toSManga(baseUrl, ::cleanTitleIfNeeded, comic = comicNode) ?: manga
        } else {
            manga
        }

        val chapterList = if (fetchChapters) {
            targets?.comicIds?.takeIf { it.isNotEmpty() }?.let { fetchAllChapters(it) } ?: emptyList()
        } else {
            chapters
        }

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchComicNode(id: String): ComicNode? {
        val payload = graphQLBody(query = COMIC_NODE_QUERY, variables = ApiComicNodeVariables(id))
        val response = client.post("$baseUrl/query/", payload)
        return response.parseGraphQLAs<ComicNodeData>().response?.data
    }

    private class TitleSource(val comicId: String, val flag: String)

    // Source cards are server-rendered on the title page; each carries a
    // language flag emoji and a link to /source/{comicId}.
    private suspend fun fetchTitleSources(titleId: String): List<TitleSource> {
        val html = client.get("$baseUrl/title/$titleId").use { it.body.string() }
        return sourceCardRegex.findAll(html)
            .map { TitleSource(comicId = it.groupValues[2], flag = it.groupValues[1]) }
            .distinctBy { it.comicId }
            .toList()
    }

    private class TitleTargets(val comicIds: List<String>, val probeComic: ComicNode?)

    private suspend fun resolveTargetComics(sources: List<TitleSource>): TitleTargets {
        if (lang == "all") {
            return TitleTargets(sources.map { it.comicId }, null)
        }

        val mapped = mapLangCode(lang)
        val direct = mutableListOf<String>()
        val toProbe = mutableListOf<TitleSource>()
        sources.forEach { source ->
            val candidates = flagLanguageCandidates[source.flag]
            when {
                candidates == null || (mapped in candidates && candidates.size > 1) -> toProbe += source
                mapped in candidates -> direct += source.comicId
                else -> {}
            }
        }

        val probed = coroutineScope {
            toProbe.map { source ->
                async { fetchComicNode(source.comicId)?.let { source.comicId to it } }
            }.awaitAll().filterNotNull()
        }
        val matching = probed.filter { it.second.translatedLanguage == mapped }
        return TitleTargets(
            comicIds = direct + matching.map { it.first },
            probeComic = matching.firstOrNull()?.second,
        )
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
            val sources = fetchTitleSources(segments[1].substringBefore("-"))
            val first = sources.firstOrNull() ?: return null
            val comicNode = fetchComicNode(first.comicId) ?: return null
            return comicNode.titleNode?.toSManga(baseUrl, ::cleanTitleIfNeeded, comic = comicNode)
        }
        return null
    }

    override fun getMangaUrl(manga: SManga): String {
        val urlPath = manga.memo["urlPath"]?.string
        return if (urlPath != null) "$baseUrl$urlPath" else "$baseUrl/title/${manga.url}"
    }

    // ============================= Chapters ==============================
    private suspend fun fetchAllChapters(comicIds: List<String>): List<SChapter> = coroutineScope {
        comicIds.map { comicId -> async { fetchChapterListPaged(comicId) } }
            .awaitAll()
            .flatten()
            .sortedByDescending { it.chapter_number }
    }

    private suspend fun fetchChapterListPaged(comicId: String): List<SChapter> {
        val pageSize = 100

        val firstPage = fetchChapterListPage(comicId, 1, pageSize)
        val allChapters = firstPage.chapters.toMutableList()
        val totalItems = firstPage.total ?: 0

        if (totalItems > pageSize && firstPage.hasNextPage) {
            val totalPages = (totalItems + pageSize - 1) / pageSize

            (2..totalPages).chunked(3).forEach { batch ->
                allChapters.addAll(
                    coroutineScope {
                        batch.map { pageNum ->
                            async { fetchChapterListPage(comicId, pageNum, pageSize).chapters }
                        }.awaitAll().flatten()
                    },
                )
            }
        }

        return allChapters
    }

    private suspend fun fetchChapterListPage(comicId: String, page: Int, pageSize: Int): ChapterListPage {
        val select = ApiChapterListSelect(
            comicId = comicId,
            page = page,
            size = pageSize,
        )

        val payload = graphQLBody(query = CHAPTER_LIST_QUERY, variables = ApiChapterListWrapper(select))
        val response = client.post("$baseUrl/query/", payload)

        val data = response.parseGraphQLAs<ChapterListData>().response

        return ChapterListPage(
            chapters = data.items.map { it.data.toSChapter() },
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
    }

    private fun isRemoveTitleVersion(): Boolean = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private fun customRemoveTitle(): String = preferences.getString(REMOVE_TITLE_CUSTOM_PREF, "")!!
    private fun isIgnoreGenreBlocklist(): Boolean = preferences.getBoolean(IGNORE_GENRE_BLOCKLIST_PREF, false)

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

        private val idQueryRegex = Regex("^id\\s*:?\\s*([a-zA-Z0-9-_]+)\\s*$", RegexOption.IGNORE_CASE)

        private const val BROWSE_PAGE_SIZE = 36

        private val sourceCardRegex = Regex("""font-family-NotoColorEmoji[^>]*>([^<]+)</span><a href="/source/([a-z0-9]+)"""")

        private val filterValueRegex = Regex("""^[a-z0-9][a-z0-9_]*$""")

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
