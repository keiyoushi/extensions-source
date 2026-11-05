package eu.kanade.tachiyomi.extension.zh.manwamanhua

import android.util.Base64
import androidx.preference.ListPreference
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
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesDecryptWithPrependedIv
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ManwaManhua :
    KeiSource(),
    ConfigurableSource {

    override val supportsRelatedMangas = true

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.host == baseUrl.toHttpUrl().host }
        addCookie { listOf("typeId" to preferences.getString(PREF_TYPE, "0")!!) }
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.isSuccessful && request.url.encodedPath.startsWith("/en_images/")) {
                response.aesDecryptWithPrependedIv(IMAGE_KEY, 16)
            } else {
                response
            }
        }
    }

    // Domains from the site's address page, which opens one that answers its /check
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> {
        val urls = ENCODED_URL_REGEX.findAll(client.get(ADDRESS_PAGE, Fetch.DOCUMENT).body.string())
            .map { String(Base64.decode(it.groupValues[1], Base64.DEFAULT)) }
            .distinct()
        val live = urls.firstOrNull { url ->
            val check = withTimeoutOrNull(3.seconds) {
                try {
                    client.get("$url/check", Fetch.XHR.from(ADDRESS_PAGE), ensureSuccess = false).body.string()
                } catch (_: IOException) {
                    null
                }
            }
            check?.contains("mw") == true
        }
        return listOfNotNull(live)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("type", "mh")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("pageSize", SEARCH_PAGE_SIZE.toString())
                .addQueryParameter(filters.firstInstanceOrNull<SearchFieldFilter>()?.field ?: "keyword", query.trim())
                .build()
            val result = client.get(url, Fetch.XHR.from("/search/")).parseAs<ResponseDto<ListDto<ComicItemDto>>>().data
            return MangasPage(result.list.map { it.toSManga() }, page * SEARCH_PAGE_SIZE < result.total)
        }

        val tags = filters.firstInstanceOrNull<TagFilter>()?.slugs.orEmpty()
        val url = "$baseUrl/api/cate".toHttpUrl().newBuilder()
            .addPathSegment(tags.joinToString("-"))
            .build()
        val body = CateRequestDto(
            page = PageRequestDto(page, CATE_PAGE_SIZE),
            sort = filters.firstInstance<SortFilter>().value,
            comic = ComicFilterDto(
                status = filters.firstInstanceOrNull<StatusFilter>()?.value ?: -1,
                day = filters.firstInstanceOrNull<DayFilter>()?.value ?: 0,
                level = filters.firstInstanceOrNull<LevelFilter>()?.value ?: 0,
                areaId = filters.firstInstanceOrNull<AreaFilter>()?.value ?: 0,
            ),
        )
        val result = client.post(url, Fetch.XHR.from("/cate/"), body.toJsonRequestBody())
            .parseAs<ResponseDto<ListDto<CateComicDto>>>().data
        return MangasPage(result.list.map { it.toSManga() }, page * CATE_PAGE_SIZE < result.total)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host !in MIRROR_HOSTS) return null
        if (url.pathSegments.firstOrNull() != "comic") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return comicDetails(id.toString())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) comicDetails(manga.url) else manga }
        val chapterList = async { if (fetchChapters) chapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun comicDetails(id: String): SManga = client.get("$baseUrl/api/comic/$id", Fetch.XHR.from("/comic/$id"))
        .parseAs<ResponseDto<ComicDto>>().data.toSManga()

    private suspend fun chapterList(manga: SManga): List<SChapter> {
        val list = client.get("$baseUrl/api/comic/${manga.url}/chapters", Fetch.XHR.from("/comic/${manga.url}"))
            .parseAs<ResponseDto<ChapterListDto>>().data.list
            .sortedWith(compareByDescending<ChapterDto> { it.sortId }.thenByDescending { it.id })
            .map { it.toSChapter() }
        if (!preferences.getBoolean(PREF_DEDUPE, false)) return list
        val names = list.filter { it.scanlator == null }.flatMapTo(HashSet()) { listOf(it.name, it.name.replace(TRAILING_NOTE_REGEX, "")) }
        return list.filterNot { it.scanlator == TAIWAN_EDITION && it.name.replace(TAIWAN_SUFFIX_REGEX, "") in names }
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get("$baseUrl/api/comic/${manga.url}/recommends", Fetch.XHR.from(getMangaUrl(manga)))
        .parseAs<List<ComicItemDto>>()
        .map { it.toSManga() }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val first = imagePage(chapter, 1)
        val rest = coroutineScope {
            (2..first.pagination.totalPages).map { async { imagePage(chapter, it) } }.awaitAll()
        }
        return (listOf(first) + rest)
            .flatMap { it.images }
            // Missing images, which the site's reader shows as a placeholder
            .filter { it.width != -1 }
            .mapIndexed { index, image -> Page(index, imageUrl = image.url) }
    }

    private suspend fun imagePage(chapter: SChapter, page: Int): ImagesDto {
        val chapterUrl = getChapterUrl(chapter)
        val url = "$baseUrl/api/comic/image".toHttpUrl().newBuilder()
            .addPathSegment(chapterUrl.toHttpUrl().pathSegments.last())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", IMAGE_PAGE_SIZE.toString())
            .addQueryParameter("image_source", "")
            .build()
        return client.get(url, Fetch.XHR.from(chapterUrl)).parseAs<ResponseDto<ImagesDto>>().data
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/cate/", Fetch.DOCUMENT).asJsoup()
        .select(".ctag a[href^=/cate/][data-value~=.]")
        .map { it.text() to it.absUrl("href").toHttpUrl().pathSegments.last() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SearchFieldFilter())
            add(Filter.Header("以下筛选在文字搜索时无效"))
            add(SortFilter(2))
            add(StatusFilter())
            add(AreaFilter())
            add(LevelFilter())
            add(DayFilter())
            data?.parseAs<List<Pair<String, String>>>()?.let { add(TagFilter(it)) }
        },
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_TYPE
            title = "内容类型"
            entries = TYPES.map { it.first }.toTypedArray()
            entryValues = TYPES.map { it.second }.toTypedArray()
            setDefaultValue("0")
            summary = "%s（网站的「频道」设置，影响排序和分类列表）"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DEDUPE
            title = "隐藏重复的台版章节"
            summary = "同一章节另有非台版时，不显示台版"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val ADDRESS_PAGE = "https://mwmw.cc/"
        private val ENCODED_URL_REGEX = Regex(""""(aHR0c[A-Za-z0-9+/=]+)"""")

        // Domains the site's address page links to
        private val MIRROR_HOSTS = setOf(
            "manwadz.cc", "manwads.cc", "manwabb.cc", "manwacn.cc", "manwarr.cc",
            "manwaxu.cc", "manwayy.cc", "manwazz.cc", "mwmwbu.cc", "mwmwff.cc",
        )

        private val IMAGE_KEY = "0B6666A0-BB59-1381-B746-a0E4C9AC".toByteArray()

        private const val SEARCH_PAGE_SIZE = 20
        private const val CATE_PAGE_SIZE = 36
        private const val IMAGE_PAGE_SIZE = 100

        // Notes like "(修正)" after a chapter name
        private val TRAILING_NOTE_REGEX = Regex("""\s*[(（][^)）]*[)）]$""")
        private val TAIWAN_SUFFIX_REGEX = Regex("""\s*[(（][^)）]*台版[^)）]*[)）]""")

        private const val PREF_TYPE = "pref_type_id"
        private const val PREF_DEDUPE = "pref_dedupe_taiwan"
        private val TYPES = listOf("全部" to "0", "一般向" to "1", "BL向" to "2", "TL向" to "3", "GL向" to "5", "禁漫" to "4")
    }
}
