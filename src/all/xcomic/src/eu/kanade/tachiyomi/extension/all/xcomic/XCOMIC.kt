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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
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
import okhttp3.Response

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
            val id = idMatch.groupValues[1].substringBefore("-")
            return MangasPage(listOf(getMangaDetails(id)), false)
        }

        var sort: String? = null
        var letterMode = false
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
        var origStatus = ""
        var uploadStatus = ""
        var chapCount = ""

        filters.forEach { filter ->
            when (filter) {
                is DefaultSortFilter -> sort = filter.sort
                is LetterFilter -> letterMode = (filter.state == 1)
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
                is UploadStatusFilter -> uploadStatus = filter.selected
                is SortFilter -> sort = filter.selected
                is ChapterCountFilter -> chapCount = filter.selected
                else -> {}
            }
        }

        val variables = ApiComicSearchVariables(
            page = page,
            size = BROWSE_PAGE_SIZE,
            init = (page - 1) * BROWSE_PAGE_SIZE,
            sortby = sort,
            word = query.takeIf { it.isNotEmpty() } ?: "",
            where = if (letterMode) "letter" else "browse",
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
            origStatus = origStatus.takeIf { it.isNotEmpty() },
            siteStatus = uploadStatus.takeIf { it.isNotEmpty() },
            chapCount = chapCount.takeIf { it.isNotEmpty() },
            ignoreGlobalGenres = isIgnoreGenreBlocklist(),
        )

        val payload = graphQLBody(query = COMIC_ITEMS_QUERY, variables = ApiComicSearchWrapper(variables))
        val response = client.post("$baseUrl/query/", payload)
        return parseSearchManga(response)
    }

    private fun parseSearchManga(response: Response): MangasPage {
        val itemsData = response.parseGraphQLAs<SearchItemsData>()
        val mangas = itemsData.items.map { item ->
            item.data.toSManga(baseUrl, ::cleanTitleIfNeeded)
        }
        val hasNextPage = mangas.size >= BROWSE_PAGE_SIZE
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Filters ==============================
    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get("$baseUrl/search")
        val document = response.asJsoup()

        val filterMap = mutableMapOf<String, MutableList<Map<String, String>>>()
        filterMap["genres"] = mutableListOf()
        filterMap["types"] = mutableListOf()
        filterMap["demographics"] = mutableListOf()
        filterMap["contentRatings"] = mutableListOf()

        document.select("details.group").forEach { details ->
            val summaryText = details.selectFirst("summary")?.text()?.lowercase() ?: return@forEach
            val container = details.selectFirst("div.columns-2") ?: details.selectFirst("div.w-full.overflow-y-auto")
            val category = when {
                "genre" in summaryText -> "genres"
                "type" in summaryText -> "types"
                "demographic" in summaryText -> "demographics"
                "content rating" in summaryText -> "contentRatings"
                else -> null
            }

            if (category != null) {
                container?.select("div[:]")?.forEach { div ->
                    val slug = div.attr(":")
                    val name = div.selectFirst("span")?.text()?.trim()
                    if (slug.isNotEmpty() && !name.isNullOrEmpty()) {
                        filterMap[category]?.add(mapOf("name" to name, "value" to slug))
                    }
                }
            }
        }

        if (filterMap["genres"].isNullOrEmpty() && filterMap["types"].isNullOrEmpty()) {
            throw Exception("Failed to fetch filters dynamically")
        }

        val cleanMap = filterMap.mapValues { it.value.distinctBy { v -> v["value"] } }
        return cleanMap.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val parsed = data?.parseAs<Map<String, List<Map<String, String>>>>() ?: emptyMap()

        fun extractList(key: String): List<Pair<String, String>> = parsed[key]?.mapNotNull { map ->
            val name = map["name"]
            val value = map["value"]
            if (name != null && value != null) name to value else null
        } ?: emptyList()

        val dynamicGenres = extractList("genres")
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
                add(FormatFilter())
                add(GenreInModeFilter())
                add(GenreExModeFilter())
                add(Filter.Separator())
                add(OriginalStatusFilter())
                add(UploadStatusFilter())
                add(OriginalLanguageFilter())
                if (lang == "all") add(TranslationLanguageFilter())
                add(ChapterCountFilter())
                add(Filter.Separator())
                add(YearFilter())
                add(LetterFilter())
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
        val details = if (fetchDetails) getMangaDetails(manga) else manga
        val chapterList = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(details, chapterList)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga = getMangaDetails(getMangaId(manga.url))

    private suspend fun getMangaDetails(id: String): SManga {
        val payload = graphQLBody(query = COMIC_NODE_QUERY, variables = ApiComicNodeVariables(id = id))
        val response = client.post("$baseUrl/query/", payload)
        return parseMangaDetails(response)
    }

    private fun parseMangaDetails(response: Response): SManga {
        val result = response.parseGraphQLAs<ComicNodeData>()
        return result.response.data.toSManga(baseUrl, ::cleanTitleIfNeeded)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.takeIf { it.size >= 2 && it[0] == "comic" }?.get(1)
            ?.substringBefore("-") ?: return null
        return getMangaDetails(id)
    }

    override fun getMangaUrl(manga: SManga): String {
        val urlPath = manga.memo["urlPath"]?.string
        return if (urlPath != null) "$baseUrl$urlPath" else "$baseUrl/comic/${manga.url}"
    }

    private fun getMangaId(url: String): String = url

    // ============================= Chapters ==============================
    private suspend fun getChapterList(manga: SManga): List<SChapter> = coroutineScope {
        val firstPage = fetchChapterListPage(manga, 1)
        val allChapters = firstPage.chapters.toMutableList()
        val totalItems = firstPage.total ?: 0

        if (totalItems > 100 && firstPage.hasNextPage) {
            val totalPages = (totalItems + 99) / 100

            (2..totalPages).chunked(3).forEach { batch ->
                val deferredPages = batch.map { pageNum ->
                    async {
                        fetchChapterListPage(manga, pageNum).chapters
                    }
                }
                allChapters.addAll(deferredPages.awaitAll().flatten())
            }
        }

        allChapters
    }

    private suspend fun fetchChapterListPage(manga: SManga, page: Int): ChapterListPage {
        val select = ApiChapterListSelect(
            comicId = getMangaId(manga.url),
            page = page,
            size = 100,
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
        return if (urlPath != null) "$baseUrl$urlPath" else "$baseUrl/comic/chapter/${chapter.url}"
    }

    private fun getChapterId(url: String): String = url

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
        "other" -> "_t"
        else -> code
    }

    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val REMOVE_TITLE_CUSTOM_PREF = "REMOVE_TITLE_CUSTOM"
        private const val IGNORE_GENRE_BLOCKLIST_PREF = "IGNORE_GENRE_BLOCKLIST"

        private val idQueryRegex = Regex("^id\\s*:?\\s*([a-zA-Z0-9-_]+)\\s*$", RegexOption.IGNORE_CASE)

        private const val BROWSE_PAGE_SIZE = 36

        private val titleRegex: Regex =
            Regex("\\([^()]*\\)|\\{[^{}]*\\}|\\[(?:(?!]).)*]|«[^»]*»|〘[^〙]*〙|「[^」]*」|『[^』]*』|≪[^≫]*≫|﹛[^﹜]*﹜|〖[^〖〗]*〗|\uD81A\uDD0D.+?\uD81A\uDD0D|《[^》]*》|⌜.+?⌝|⟨[^⟩]*⟩|/Official|/ Official", RegexOption.IGNORE_CASE)
    }
}
