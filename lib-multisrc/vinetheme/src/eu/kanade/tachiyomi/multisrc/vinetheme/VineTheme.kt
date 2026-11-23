package eu.kanade.tachiyomi.multisrc.vinetheme

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

abstract class VineTheme :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(FragmentInterceptor())

    private val preferences by getPreferencesLazy()

    private val rscHeaders: Headers get() = headersBuilder().set("rsc", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage = getApiMangasPage(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getApiMangasPage(page, "updated")

    private suspend fun getApiMangasPage(page: Int, sort: String): MangasPage {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("contentMode", "comics")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .build()
        val data = client.get(url, Fetch.XHR).parseAs<ApiSeriesResponse>()
        return MangasPage(data.data.map { it.toSManga(baseUrl) }, data.meta?.hasMore == true)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .apply {
                addQueryParameter("limit", "24")
                addQueryParameter("contentMode", "comics")
                if (query.isNotEmpty()) {
                    addQueryParameter("q", query)
                }
                filters.filterIsInstance<UriQueryFilter>().forEach { it.addToQuery(this) }
                addQueryParameter("page", page.toString())
            }
            .build()
        val data = client.get(url, Fetch.XHR).parseAs<ApiSeriesResponse>()
        return MangasPage(data.data.map { it.toSManga(baseUrl) }, data.meta?.hasMore == true)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.getOrNull(0) != "series" || segments.getOrNull(1) != "comic") return null
        val slug = segments.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: return null
        val body = getRsc("$baseUrl/series/comic/$slug".toHttpUrl()).body.string()
        val detail = body.extractNextJsRsc<DetailDto>(DetailDto::nextJsFilter) ?: return null
        return detail.series.toSManga(baseUrl, fallbackSlug = slug, author = body.parseAuthor())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val body = getRsc(getDetailsUrl(manga)).body.string()
        val details = body.extractNextJsRsc<DetailDto>(DetailDto::nextJsFilter)

        val updatedManga = details?.series?.toSManga(baseUrl, manga, author = body.parseAuthor()) ?: manga
        val updatedChapters = if (fetchChapters) fetchAllChapters(manga, details, chapters) else chapters

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override val supportsRelatedMangas: Boolean get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = getRsc(getDetailsUrl(manga))
        .extractNextJs<RelatedDto>(RelatedDto::nextJsFilter)
        ?.similarSeries
        .orEmpty()
        .filter { it.isComic }
        .map { it.toSManga(baseUrl) }

    private fun String.parseAuthor(): String? = extractNextJsRsc<JsonLdScriptDto>(JsonLdScriptDto::nextJsFilter)
        ?.html
        ?.parseAs<JsonLdBookDto>()
        ?.author
        ?.name

    // Bare _rsc parameter, without which the site redirects every RSC fetch
    private suspend fun getRsc(url: HttpUrl) = client.get(url.newBuilder().addQueryParameter("_rsc", null).build(), rscHeaders, Fetch.XHR)

    private fun getDetailsUrl(manga: SManga): HttpUrl = getMangaUrl(manga).toHttpUrl().newBuilder()
        .addQueryParameter("sort", "desc")
        .build()

    private suspend fun fetchAllChapters(manga: SManga, details: DetailDto?, existing: List<SChapter>): List<SChapter> {
        if (details == null) return emptyList()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF, true)
        val allChapters = if (existing.isEmpty() && details.totalPages > 1) {
            details.chapters + coroutineScope {
                (2..details.totalPages).map { page ->
                    async {
                        getRsc(
                            getDetailsUrl(manga).newBuilder()
                                .addQueryParameter("page", page.toString())
                                .build(),
                        ).extractNextJs<DetailDto>(DetailDto::nextJsFilter)
                            ?.chapters.orEmpty()
                    }
                }.awaitAll().flatten()
            }
        } else {
            details.chapters
        }
        val fetched = allChapters
            .filter { !(it.isLocked && hideLocked) }
            .map { it.toSChapter(manga, details.series.team?.name) }
        val retained = existing.filter {
            !(hideLocked && it.memo["isLocked"]?.booleanOrNull == true)
        }
        return (fetched + retained)
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["isLocked"]?.booleanOrNull == true) throw Exception("This chapter is locked")
        val chapterUrl = getChapterUrl(chapter)
        val pages = getRsc(chapterUrl.toHttpUrl())
            .extractNextJs<ChapterDetailDto>(ChapterDetailDto::nextJsFilter)
            ?.chapter?.pages.orEmpty()
        val masterKey = if (pages.any { it.fragmentMap != null }) {
            client.get("$baseUrl/api/fragments/${chapter.memo["id"]!!.string}/key", Fetch.XHR.from(chapterUrl))
                .parseAs<FragmentKeyDto>().key.decodeBase64()
        } else {
            null
        }
        return pages.mapIndexed { index, page ->
            val imageUrl = if (page.fragmentMap != null) page.toLayoutUrl(masterKey!!) else page.imageUrl!!.toAbsoluteUrl(baseUrl)
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun PageDto.toLayoutUrl(masterKey: ByteArray): String {
        val pageKey = id.hmacSha256(masterKey)
        val map = fragmentMap!!.decodeBase64()
            .aesDecrypt(aesGcm(pageKey, fragmentMapIv!!.decodeBase64()))
            .decodeToString()
            .parseAs<FragmentMapDto>()
        val urls = fragments.associate { it.fragmentIndex to it.imageUrl.toAbsoluteUrl(baseUrl)!! }
        val layout = PageLayout(
            map.w,
            map.h,
            map.fragments.map { PagePart(urls[it.idx]!!, it.x, it.y, it.w, it.h) },
        )
        return layout.parts.first().url.toHttpUrl().newBuilder()
            .fragment(layout.toJsonString())
            .build()
            .toString()
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client
        .get("$baseUrl/series", Fetch.DOCUMENT)
        .extractNextJs<GenreTagListDto>(GenreTagListDto::nextJsFilter)
        ?.toFilterData()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()?.apply {
            if (outdated) throw Exception("Filter data outdated")
        }

        return FilterList(
            listOfNotNull(
                filterData?.genres?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
                filterData?.tags?.takeIf { it.isNotEmpty() }?.let(::TagFilter),
                TypeFilter(),
                StatusFilter(),
                OriginFilter(),
                SaleFilter(),
                ChapterFilter(),
                SortFilter(),
            ),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF
            title = "Hide locked chapters"
            summary = "Hide chapters that require coins to read"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]?.string.orEmpty()
        return "$baseUrl/series/comic/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.string
        val number = chapter.memo["number"]!!.string
        return "$baseUrl/series/comic/$slug/chapter/$number"
    }

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.DEFAULT)

    companion object {
        private const val HIDE_LOCKED_PREF = "pref_hide_locked_chapters"
    }
}
