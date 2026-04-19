package eu.kanade.tachiyomi.extension.en.reimanga

import android.app.Application
import android.util.Log
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.emptySet

const val DOMAIN = "reimanga.com"

class Reimanga :
    HttpSource(),
    ConfigurableSource {
    override val name = "ReiManga"
    override val lang = "en"
    override val baseUrl = "https://$DOMAIN"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(
            CookieInterceptor(DOMAIN, "showAdultContent" to "true"),
        )
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (page > 1) {
            val filters = getFilterList().apply {
                firstInstance<SortFilter>().state = Filter.Sort.Selection(2, false)
            }
            return fetchSearchManga(page - 1, "", filters)
        }

        return super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/manga/trending?limit=100", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<List<Manga>>()
        val mangas = data.map { it.toSManga() }

        return MangasPage(mangas, true)
    }

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList())

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host == DOMAIN && url.pathSegments[0] == "manga") {
                val slug = url.pathSegments[1]
                val tmpManga = SManga.create().apply {
                    this@apply.url = slug
                }

                return fetchMangaDetails(tmpManga)
                    .map { MangasPage(listOf(it), false) }
            } else {
                throw Exception("Unsupported Url")
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

        return GET(url, headers)
    }

    private val tagsCacheFile by lazy {
        Injekt.get<Application>().cacheDir
            .resolve("source_$id")
            .also { it.mkdirs() }
            .resolve("tags.json")
    }

    private fun isCacheValid(): Boolean {
        if (!tagsCacheFile.exists()) return false
        val ageMs = System.currentTimeMillis() - tagsCacheFile.lastModified()
        return ageMs <= 86_400_000L // 1 day
    }

    private val isFetchingTags = AtomicBoolean(false)
    private val tagFetchFailures = AtomicInteger(0)

    override fun getFilterList(): FilterList {
        val filters = mutableListOf(
            SortFilter(),
            StatusFilter(),
        )

        val cachedTags = runCatching {
            tagsCacheFile.readText().parseAs<TagList>()
        }.getOrNull()

        if (cachedTags != null) {
            val excluded = preferences.getStringSet(EXCLUDE_TAG_PREF, emptySet())!!
            val genres = cachedTags.genres.map { tag ->
                val isExcluded = tag.slug in excluded
                val state = if (isExcluded) {
                    Filter.TriState.STATE_EXCLUDE
                } else {
                    Filter.TriState.STATE_IGNORE
                }

                TriStateOption(tag.name, tag.slug, state)
            }.sortedBy { it.name }
            val tags = cachedTags.tags.map { tag ->
                val isExcluded = tag.slug in excluded
                val state = if (isExcluded) {
                    Filter.TriState.STATE_EXCLUDE
                } else {
                    Filter.TriState.STATE_IGNORE
                }

                TriStateOption(tag.name, tag.slug, state)
            }.sortedBy { it.name }

            filters.add(GenreFilter(genres))
            filters.add(TagFilter(tags))
        } else {
            filters.add(Filter.Separator())
            filters.add(Filter.Header("Press 'reset' to load tags & genres"))
        }

        if (!isCacheValid() && tagFetchFailures.get() < 3 && isFetchingTags.compareAndSet(false, true)) {
            val request = GET("$baseUrl/advanced-search", rscHeaders)
            client.newCall(request).enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        runCatching {
                            val tagList = response.extractNextJs<TagList>()!!
                            tagsCacheFile.writeText(tagList.toJsonString())
                            tagFetchFailures.set(0)
                        }.onFailure { e ->
                            Log.d(name, "Failed to parse tags", e)
                            tagFetchFailures.incrementAndGet()
                        }
                        isFetchingTags.set(false)
                    }
                    override fun onFailure(call: Call, e: IOException) {
                        Log.d(name, "Failed to fetch tags", e)
                        tagFetchFailures.incrementAndGet()
                        isFetchingTags.set(false)
                    }
                },
            )
        }

        return FilterList(filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaList>()
        val mangas = data.data.map { it.toSManga() }
        val hasNextPage = data.pagination.hasNextPage()

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("-")

        return GET("$baseUrl/api/manga/$mangaId", headers)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaPage>()

        return data.manga.toSManga()
    }

    override fun relatedMangaListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("-")

        return GET("$baseUrl/api/manga/$mangaId/similar", headers)
    }

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val data = response.parseAs<List<Manga>>()

        return data.map { it.toSManga() }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<ChapterList>()
            ?: return emptyList()

        return data.chapters.map { chapter ->
            SChapter.create().apply {
                url = "${data.manga.slug}-${data.manga.id}/${chapter.id}"
                name = chapter.name.replace(spaceRegex, " ").trim()
                date_upload = dateFormat.tryParse(chapter.uploadDate ?: chapter.updatedAt ?: chapter.createdAt)
            }
        }
    }

    private val spaceRegex = Regex("""\s+""")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), rscHeaders)

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<Images>()

        return data?.images.orEmpty().mapIndexed { index, image ->
            Page(index, imageUrl = image.url)
        }
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

                    updated.values = newSet
                    updateSummary(updated, newSet)
                    false
                }
            }.also(screen::addPreference)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

const val EXCLUDE_TAG_PREF = "pref_exclude_tag"
