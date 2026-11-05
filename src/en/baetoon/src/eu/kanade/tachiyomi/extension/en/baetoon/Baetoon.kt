package eu.kanade.tachiyomi.extension.en.baetoon

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Baetoon :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val rscHeaders get() = headersBuilder().set("rsc", "1").build()

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override suspend fun getPopularManga(page: Int) = searchTitles(page, "most-view")

    override suspend fun getLatestUpdates(page: Int) = searchTitles(page, "lastest-updates")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = searchTitles(
        page,
        filters.firstInstanceOrNull<SortFilter>()?.value ?: "most-view",
    ) {
        if (query.isNotBlank()) addQueryParameter("text", query.trim())
        filters.firstInstanceOrNull<AuthorFilter>()?.state?.takeIf { it.isNotBlank() }?.let { addQueryParameter("author", it.trim()) }
        filters.firstInstanceOrNull<KeywordFilter>()?.state?.takeIf { it.isNotBlank() }?.let { addQueryParameter("keywords", it.trim()) }
        filters.firstInstanceOrNull<GenreFilter>()?.value?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("tag", it) }
    }

    private suspend fun searchTitles(page: Int, sort: String, params: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val url = "$baseUrl/api/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", sort)
            params()
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
        }.build()
        val result = client.get(url, Fetch.XHR.from("/advanced-search")).parseAs<SearchDto>()
        return MangasPage(result.data.map { it.toSManga() }, result.pagination.page < result.pagination.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1) ?: return null
        return when (url.pathSegments[0]) {
            "title-detail" -> fetchDetail(getMangaUrl(SManga.create().apply { this.url = id })).toSManga().apply { initialized = true }
            "chapter-detail" -> fetchReader(getChapterUrl(SChapter.create().apply { this.url = id })).toSManga()
            else -> null
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/title-detail/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = fetchDetail(getMangaUrl(manga))
        val mode = preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP)
        val preferred = preferences.groups(PREF_PREFERRED_GROUPS)
        val blocked = preferences.groups(PREF_BLOCKED_GROUPS)
        return SMangaUpdate(detail.toSManga(), detail.chapters.flatMap { it.toSChapters(mode, preferred, blocked) })
    }

    override suspend fun fetchRelatedMangaList(manga: SManga) = fetchDetail(getMangaUrl(manga)).recommendations.map { it.toSManga() }

    private suspend fun fetchDetail(url: String): DetailDto = fetchRsc<DetailDto>(url)
        ?: throw Exception("Title not found")

    private fun ChapterDto.toSChapters(mode: String?, preferred: List<String>, blocked: List<String>): List<SChapter> {
        val uploads = sources.filterNot { it.sourceName.lowercase() in blocked }
        return when (mode) {
            // The site lists a source's repeat uploads of a chapter under the same name
            ALL_UPLOADS -> uploads.mapIndexed { index, upload ->
                val copy = uploads.take(index + 1).count { it.sourceName == upload.sourceName }
                upload.toSChapter(this, if (copy > 1) "${upload.sourceName} ($copy)" else upload.sourceName)
            }
            ONE_PER_CHAPTER -> {
                val upload = preferred.firstNotNullOfOrNull { group -> uploads.firstOrNull { it.sourceName.lowercase() == group } }
                    ?: uploads.firstOrNull()
                listOfNotNull(upload?.toSChapter(this))
            }
            else -> uploads.distinctBy { it.sourceName }.map { it.toSChapter(this) }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter-detail/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchReader(getChapterUrl(chapter)).pages.mapIndexed { index, url ->
        Page(index, imageUrl = url)
    }

    private suspend fun fetchReader(url: String): ReaderDto = fetchRsc<ReaderDto>(url)
        ?: throw Exception("Chapter not found")

    override suspend fun fetchFilterData(): JsonElement = fetchRsc<GenresDto>("$baseUrl/advanced-search")!!.initialGenres.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            SortFilter(),
            AuthorFilter(),
            KeywordFilter(),
            data?.parseAs<List<String>>()?.let(::GenreFilter),
        ),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val preferredGroups = EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_GROUPS
            title = "Preferred sources"
            summary = "Source names, comma-separated, as shown on each chapter. " +
                "One per chapter shows the first listed source that has the chapter, otherwise the one the site opens. Default: none"
            dialogTitle = "Preferred sources"
            setDefaultValue("")
            setEnabled(preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP) == ONE_PER_CHAPTER)
        }

        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_LIST
            title = "Chapter list"
            entries = arrayOf("One per source", "All uploads, like the site's list", "One per chapter")
            entryValues = arrayOf(ONE_PER_GROUP, ALL_UPLOADS, ONE_PER_CHAPTER)
            summary = "%s"
            setDefaultValue(ONE_PER_GROUP)
            setOnPreferenceChangeListener { _, newValue ->
                preferredGroups.setEnabled(newValue == ONE_PER_CHAPTER)
                true
            }
        }.let(screen::addPreference)

        screen.addPreference(preferredGroups)

        EditTextPreference(screen.context).apply {
            key = PREF_BLOCKED_GROUPS
            title = "Blocked sources"
            summary = "Source names, comma-separated. Their uploads are left out of the chapter list"
            dialogTitle = "Blocked sources"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    // Without the bare `_rsc` param the site answers an RSC fetch with a 307 to it
    private suspend inline fun <reified T> fetchRsc(url: String): T? = client.get(
        url.toHttpUrl().newBuilder().addQueryParameter("_rsc", null).build(),
        rscHeaders,
        Fetch.XHR,
    ).extractNextJs<T>()

    private fun SharedPreferences.groups(key: String) = getString(key, "")!!
        .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    companion object {
        private const val PREF_CHAPTER_LIST = "pref_chapter_list"
        private const val PREF_PREFERRED_GROUPS = "pref_preferred_groups"
        private const val PREF_BLOCKED_GROUPS = "pref_blocked_groups"
        private const val ONE_PER_GROUP = "group"
        private const val ALL_UPLOADS = "all"
        private const val ONE_PER_CHAPTER = "chapter"
    }
}
