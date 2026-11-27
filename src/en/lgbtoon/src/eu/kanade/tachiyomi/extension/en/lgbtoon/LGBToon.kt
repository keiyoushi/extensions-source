package eu.kanade.tachiyomi.extension.en.lgbtoon

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class LGBToon :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val subgenre get() = preferences.getString(SUBGENRE_PREF, "")!!.ifEmpty { null }

    override suspend fun getPopularManga(page: Int) = getTitles(titlesUrl(page, "views_7d:desc").addSubgenre(subgenre).build())

    override suspend fun getLatestUpdates(page: Int) = getTitles(titlesUrl(page, LATEST).addSubgenre(subgenre).build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = titlesUrl(page, filters.firstInstanceOrNull<SortFilter>()?.value ?: LATEST).apply {
            if (query.isNotBlank()) addQueryParameter("keyword", query.trim())
            addSubgenre(filters.firstInstanceOrNull<SubgenreFilter>()?.value)
            filters.firstInstanceOrNull<TypeFilter>()?.let { addListParameter("type", it.selected) }
            filters.firstInstanceOrNull<TagFilter>()?.let {
                addListParameter("tag", it.included)
                addListParameter("genres_ex", it.excluded)
            }
        }.build()
        return getTitles(url)
    }

    // A series is "/title/<slug>-<id>"; the site also opens it by the slug or the id alone, as in its reader's links
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "title") return null
        val series = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/title/$series" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val title = client.get(getMangaUrl(manga), Fetch.DOCUMENT).extractNextJs<TitleDto>()!!
        return SMangaUpdate(title.toSManga(), title.chapters.map { it.toSChapter(title.url) })
    }

    // The site lists versions it doesn't host, and sends readers to another chapter instead: they have no pages
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT)
        .extractNextJs<ReaderDto>()?.initialPages.orEmpty()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(subgenre)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = SUBGENRE_PREF
            title = "Subgenre"
            entries = SUBGENRES.map { it.first }.toTypedArray()
            entryValues = SUBGENRES.map { it.second.orEmpty() }.toTypedArray()
            setDefaultValue("")
            summary = "%s\nShown in Popular and Latest, and the default of the Subgenre filter"
        }.let(screen::addPreference)
    }

    private fun titlesUrl(page: Int, sort: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("api/titles")
        .addQueryParameter("sort", sort)
        .addQueryParameter("page", page.toString())
        .addQueryParameter("limit", "24")

    private fun HttpUrl.Builder.addSubgenre(value: String?) = apply {
        if (value != null) addQueryParameter("demographics", value)
    }

    private fun HttpUrl.Builder.addListParameter(name: String, values: List<String>) {
        if (values.isNotEmpty()) addQueryParameter(name, values.joinToString(","))
    }

    private suspend fun getTitles(url: HttpUrl): MangasPage {
        val data = client.get(url, Fetch.XHR).parseAs<TitlesDto>().data
        return MangasPage(data.items.map { it.toSManga() }, data.page < data.totalPages)
    }

    companion object {
        private const val LATEST = "chapter_updated_at:desc"
        private const val SUBGENRE_PREF = "subgenre"
    }
}
