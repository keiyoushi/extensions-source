package eu.kanade.tachiyomi.extension.uk.mangainua

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class Mangainua :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy {
        if (contains("site_hidden_tags_search")) {
            edit().remove("site_hidden_tags_search").apply()
        }
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.encodedPath.contains("/ajax/") }
        rateLimit(5)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = makeSearchRequest("pop", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = makeSearchRequest("new", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeSearchRequest("pop", page, filters, query)

    private suspend fun makeSearchRequest(sortBy: String, page: Int, filters: FilterList? = null, query: String? = null): MangasPage {
        val url = "$baseUrl/engine/ajax/mgsearch.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("mode", "catalog")
            var sort = sortBy

            filters?.forEach { filter ->
                when (filter) {
                    is TagFilter -> {
                        filter.included?.let { addQueryParameter("g", it.joinToString(",")) }
                        filter.excluded?.let { addQueryParameter("xg", it.joinToString(",")) }
                    }
                    is StatusFilter -> filter.selected?.let { addQueryParameter("st", it.joinToString(",")) }
                    is CategoriesFilter -> filter.selected?.let { addQueryParameter("type", it.joinToString(",")) }
                    is AgeFilter -> filter.selected?.let { addQueryParameter("age", it.joinToString(",")) }
                    is SizeFilter -> {
                        filter.min?.let { addQueryParameter("c1", "$it.1") }
                        filter.max?.let { addQueryParameter("c2", "$it") }
                    }
                    is YearsFilter -> {
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                        filter.min?.coerceIn(1961, currentYear)?.let { addQueryParameter("y1", "$it") }
                        filter.max?.coerceIn(1961, currentYear)?.let { addQueryParameter("y2", "$it") }
                    }
                    is SortFilter -> filter.selected?.let { sort = it }
                    else -> {}
                }
            }

            if (filters.isNullOrEmpty()) {
                val ignoredTagsSettings = ignoreTags()
                if (ignoredTagsSettings.isNotEmpty()) addQueryParameter("xg", ignoredTagsSettings.joinToString(","))
            }

            addQueryParameter("sort", sort)
            addQueryParameter("page", page.toString())
            addQueryParameter("per", PAGINATION.toString())
            if (!query.isNullOrEmpty()) addQueryParameter("q", query)
        }.build()

        client.get(url, Fetch.XHR).use { response ->
            val data = response.parseAs<SearchWrapper>()
            val mangas = data.items.mapNotNull { it.toSManga(baseUrl) }
            return MangasPage(mangas, page < data.pages!!)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "mangas" && url.encodedPath.endsWith(".html")) {
            val tmpManga = SManga.create().apply {
                this.url = url.encodedPath
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val newUrl = manga.url
        val mangaId = manga.memo.getStringOrNull("id") ?: newUrl.getMangaId()

        val mangaNew = async {
            if (fetchDetails) {
                val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
                SManga.create().apply {
                    url = newUrl
                    title = document.selectFirst(".mt-title")!!.ownText()
                    description = MarkdownDescription.Builder().apply {
                        rating(
                            document.selectFirst(".mgr-total .mgr-total__val")?.text()?.toDoubleOrNull(),
                            votes = document.selectFirst(".mgr-summary__meta span[^data-votes]")?.text()?.toIntOrNull(),
                        )
                        description(document.selectFirst(".mt-desc")?.text())
                    }.build()
                    thumbnail_url = document.selectFirst(".mt-cover img")?.imgAttr()
                    status = when (document.selectFirst(".mt-status a")?.text()) {
                        "Триває" -> SManga.ONGOING
                        "Заморожено" -> SManga.ON_HIATUS
                        "Покинуто" -> SManga.CANCELLED
                        "Закінчений" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                    genre = buildList {
                        add(document.selectFirst(".mt-tag--type")?.text())
                        addAll(document.select(".mt-genres a").eachText())
                    }.joinToString()
                    memo = buildJsonObject {
                        put("id", mangaId)
                    }
                }
            } else {
                manga
            }
        }

        val chaptersNew = async {
            if (fetchChapters) {
                val body = FormBody.Builder()
                    .add("news_id", mangaId)
                    .build()
                val data = client.post("$baseUrl/engine/ajax/controller.php?mod=mgchapters", Fetch.XHR, body).parseAs<ChaptersWrapper>()
                data.chapters.map { it.toSChapter() }.asReversed()
            } else {
                chapters
            }
        }

        SMangaUpdate(mangaNew.await(), chaptersNew.await())
    }

    private fun String.getMangaId() = mangaIdRegex.find(this.substringAfterLast("/"))!!.value

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return data.select(".rd-pages img").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgAttr())
        }
    }

    override val supportsRelatedMangas: Boolean = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = "$baseUrl/engine/ajax/controller.php?mod=mgsimilar&id=${manga.memo["id"]!!.string}"
        val data = client.get(url, Fetch.XHR).parseAs<SearchWrapper>()
        return data.items.mapNotNull { it.toSManga(baseUrl) }
    }

    override val supportsFilterFetching: Boolean = true

    override suspend fun fetchFilterData(): JsonElement {
        val data = client.get("$baseUrl/engine/ajax/mgsearch.php?mode=meta", Fetch.XHR).parseAs<GenreList>()
        return data.genres.sortedBy { it.name }.map { it.name to it.id.toString() }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(SortFilter("pop"))
        data?.parseAs<List<Pair<String, String>>>()?.let {
            if (it.isNotEmpty()) {
                TagFilter.options = it
                filters.add(TagFilter(ignoreTags()))
            }
        }
        filters.addAll(
            listOf(
                CategoriesFilter(),
                StatusFilter(),
                AgeFilter(),
                SizeFilter(),
                YearsFilter(),
            ),
        )
        return FilterList(filters)
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    private fun ignoreTags(): Set<String> = preferences.getStringSet(SITE_TAGS_PREF, emptySet()) ?: emptySet()
    private fun ignoreGenresTitles(): String? = preferences.getString(SITE_TAGS_PREF_TITLES, null)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = SITE_TAGS_PREF
            title = SITE_TAGS_PREF_TITLE
            val tags = TagFilter.options
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = (ignoreGenresTitles() ?: "Не вибрано") + SITE_TAGS_PREF_SUM
            dialogTitle = "Виберіть категорії які потрібно сховати"
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = tags.filter { it.second in selected }
                    .joinToString { it.first }
                    .ifEmpty { "Не вибрано" } + SITE_TAGS_PREF_SUM
                preferences.edit().putString(
                    SITE_TAGS_PREF_TITLES,
                    tags.filter { it.second in selected }
                        .joinToString { it.first }
                        .ifEmpty { "Не вибрано" },
                ).apply()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val PAGINATION = 36
        private const val SITE_TAGS_PREF = "site_hidden_tags"
        private const val SITE_TAGS_PREF_TITLES = "site_hidden_tags_titles"
        private const val SITE_TAGS_PREF_TITLE = "Приховані категорії"
        private const val SITE_TAGS_PREF_SUM = "\n\nⓘЦі категорії завжди будуть приховані в 'Популярне', 'Новинки' та 'Фільтр'.\n\nⓘЯкщо список категорій порожній, зайдіть 'Огляд' -> 'Джерела' -> Manga/in/UA -> 'Фільтр' та натисніть 'Скинути'"
        private val mangaIdRegex = """^(\d+)""".toRegex()
    }
}
