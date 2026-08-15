package eu.kanade.tachiyomi.extension.en.reimanga

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.IOException
import kotlin.time.Instant

@Source
abstract class Reimanga :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("showAdultContent" to "true")
        protocols(listOf(Protocol.HTTP_1_1))
    }

    private val preferences by getPreferencesLazy()

    private val rscHeaders by lazy {
        headers.newBuilder()
            .set("rsc", "1")
            .build()
    }

    private val tagsCacheFile by lazy {
        applicationContext.cacheDir
            .resolve("source_$id")
            .also { it.mkdirs() }
            .resolve("tags.json")
    }

    private val spaceRegex = Regex("""\s+""")

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) {
            val filters = getFilterList().apply {
                firstInstance<SortFilter>().state = Filter.Sort.Selection(2, false)
            }
            return getSearchMangaList(page - 1, "", filters)
        }

        val data = client.get("$baseUrl/api/manga/trending?limit=100").parseAs<List<Manga>>()
        return MangasPage(data.map { it.toSManga(baseUrl) }, true)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
            }
            val excluded = mutableListOf<String>()
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.sort)
                        addQueryParameter("order", filter.direction)
                    }
                    is StatusFilter -> {
                        filter.status?.also { status ->
                            addQueryParameter("status", status)
                        }
                    }
                    is GenreFilter -> {
                        val included = filter.included
                        if (included.isNotEmpty()) {
                            addQueryParameter("genre", included.joinToString(","))
                        }
                        excluded.addAll(filter.excluded)
                    }
                    is TagFilter -> {
                        val included = filter.included
                        if (included.isNotEmpty()) {
                            addQueryParameter("tag", included.joinToString(","))
                        }
                        excluded.addAll(filter.excluded)
                    }
                    else -> {}
                }
            }
            if (excluded.isNotEmpty()) {
                addQueryParameter("excludeGenres", excluded.joinToString(","))
            }
        }.build()

        val data = client.get(url).parseAs<MangaList>()
        return MangasPage(data.data.map { it.toSManga(baseUrl) }, data.pagination.hasNextPage())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return fetchMangaUpdate(
            SManga.create().apply { this.url = slug },
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga.apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url.substringAfterLast("-")

        val detailsDeferred = async {
            if (fetchDetails) {
                client.get("$baseUrl/api/manga/$mangaId")
                    .parseAs<MangaPage>()
                    .manga
                    .toSManga(baseUrl)
            } else {
                null
            }
        }
        val chaptersDeferred = async {
            if (fetchChapters) {
                parseChapterList(
                    client.get(getMangaUrl(manga), rscHeaders).extractNextJs<ChapterList>(),
                )
            } else {
                null
            }
        }

        SMangaUpdate(
            manga = detailsDeferred.await() ?: manga,
            chapters = chaptersDeferred.await() ?: chapters,
        )
    }

    private fun parseChapterList(data: ChapterList?): List<SChapter> {
        if (data == null) return emptyList()

        return data.chapters.map { chapter ->
            SChapter.create().apply {
                url = "${data.manga.slug}-${data.manga.id}/${chapter.id}"
                name = chapter.name.replace(spaceRegex, " ").trim()
                date_upload = Instant.tryParse(
                    chapter.uploadDate ?: chapter.updatedAt ?: chapter.createdAt,
                )
            }
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val mangaId = manga.url.substringAfterLast("-")
        return client.get("$baseUrl/api/manga/$mangaId/similar")
            .parseAs<List<Manga>>()
            .map { it.toSManga(baseUrl) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(getChapterUrl(chapter), rscHeaders).extractNextJs<Images>()
            ?: return emptyList()

        return data.images.mapIndexed { index, image ->
            Page(index, imageUrl = image.url)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val tagList = client.get("$baseUrl/advanced-search", rscHeaders)
            .extractNextJs<TagList>()
            ?: throw IOException("Failed to extract tags")
        tagsCacheFile.writeText(tagList.toJsonString())
        return tagList.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            StatusFilter(),
        )

        val cachedTags = data?.parseAs<TagList>()
        if (cachedTags != null) {
            val excluded = preferences.getStringSet(EXCLUDE_TAG_PREF, emptySet()) ?: emptySet()
            val genres = cachedTags.genres.map { tag ->
                TriStateOption(
                    tag.name,
                    tag.slug,
                    if (tag.slug in excluded) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE,
                )
            }.sortedBy { it.name }
            val tags = cachedTags.tags.map { tag ->
                TriStateOption(
                    tag.name,
                    tag.slug,
                    if (tag.slug in excluded) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE,
                )
            }.sortedBy { it.name }

            filters.add(GenreFilter(genres))
            filters.add(TagFilter(tags))
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val cachedTags = runCatching {
            tagsCacheFile.readText().parseAs<TagList>()
        }.getOrNull()

        if (cachedTags != null) {
            val tags = buildList {
                addAll(cachedTags.genres)
                addAll(cachedTags.tags)
            }.sortedBy { it.name }
            MultiSelectListPreference(screen.context).apply {
                key = EXCLUDE_TAG_PREF
                title = "Exclude Tags from Browse"
                entries = tags.map { it.name }.toTypedArray()
                entryValues = tags.map { it.slug }.toTypedArray()
                setDefaultValue(emptySet<String>())

                fun updateSummary(pref: MultiSelectListPreference, selected: Set<String>?) {
                    pref.summary = if (selected.isNullOrEmpty()) {
                        "None"
                    } else {
                        val entryMap = pref.entryValues.zip(pref.entries).toMap()
                        selected.joinToString { entryMap[it] ?: it }
                    }
                }

                updateSummary(this, preferences.getStringSet(EXCLUDE_TAG_PREF, emptySet()))

                setOnPreferenceChangeListener { pref, newValue ->
                    @Suppress("UNCHECKED_CAST")
                    val updated = pref as MultiSelectListPreference

                    @Suppress("UNCHECKED_CAST")
                    val newSet = newValue as Set<String>

                    updateSummary(updated, newSet)
                    true
                }
            }.also(screen::addPreference)
        }
    }
}

const val EXCLUDE_TAG_PREF = "pref_exclude_tag"
