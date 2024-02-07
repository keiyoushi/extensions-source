package eu.kanade.tachiyomi.extension.en.mangafun

import android.util.Base64
import android.util.Log
import eu.kanade.tachiyomi.extension.en.mangafun.MangaFunUtils.toSChapter
import eu.kanade.tachiyomi.extension.en.mangafun.MangaFunUtils.toSManga
import eu.kanade.tachiyomi.lib.lzstring.LZString
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

class MangaFun : HttpSource() {

    override val name = "Manga Fun"

    override val baseUrl = "https://mangafun.me"

    private val apiUrl = "https://a.mangafun.me/v0"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    private val json: Json by injectLazy()

    private val nextBuildId by lazy {
        val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()

        json.parseToJsonElement(
            document.selectFirst("#__NEXT_DATA__")!!.data(),
        )
            .jsonObject["buildId"]!!
            .jsonPrimitive
            .content
    }

    private lateinit var directory: List<MinifiedMangaDto>

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { popularMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/title/all", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        directory = response.parseAs<List<MinifiedMangaDto>>()
            .sortedBy { it.rank }
        return parseDirectory(1)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(latestUpdatesRequest(page))
                .asObservableSuccess()
                .map { latestUpdatesParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        directory = response.parseAs<List<MinifiedMangaDto>>()
            .sortedByDescending { MangaFunUtils.convertShortTime(it.updatedAt) }
        return parseDirectory(1)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val slug = query.removePrefix(PREFIX_ID_SEARCH)
            return fetchMangaDetails(SManga.create().apply { url = "/title/$slug" })
                .map { MangasPage(listOf(it), false) }
        } else if (page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it, query, filters) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        popularMangaRequest(page)

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()

    private fun searchMangaParse(response: Response, query: String, filters: FilterList): MangasPage {
        directory = response.parseAs<List<MinifiedMangaDto>>()
            .filter {
                it.name.contains(query, false) ||
                    it.alias.any { a -> a.contains(query, false) }
            }

        filters.ifEmpty { getFilterList() }.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val included = mutableListOf<Int>()
                    val excluded = mutableListOf<Int>()

                    filter.state.forEach { g ->
                        when (g.state) {
                            Filter.TriState.STATE_INCLUDE -> included.add(g.id)
                            Filter.TriState.STATE_EXCLUDE -> excluded.add(g.id)
                        }
                    }

                    if (included.isNotEmpty()) {
                        directory = directory
                            .filter { it.genres.any { g -> included.contains(g) } }
                    }

                    if (excluded.isNotEmpty()) {
                        directory = directory
                            .filterNot { it.genres.any { g -> excluded.contains(g) } }
                    }
                }
                is TypeFilter -> {
                    val included = mutableListOf<Int>()
                    val excluded = mutableListOf<Int>()

                    filter.state.forEach { g ->
                        when (g.state) {
                            Filter.TriState.STATE_INCLUDE -> included.add(g.id)
                            Filter.TriState.STATE_EXCLUDE -> excluded.add(g.id)
                        }
                    }

                    if (included.isNotEmpty()) {
                        directory = directory
                            .filter { included.any { t -> it.titleType == t } }
                    }

                    if (excluded.isNotEmpty()) {
                        directory = directory
                            .filterNot { excluded.any { t -> it.titleType == t } }
                    }
                }
                is StatusFilter -> {
                    val included = mutableListOf<Int>()
                    val excluded = mutableListOf<Int>()

                    filter.state.forEach { g ->
                        when (g.state) {
                            Filter.TriState.STATE_INCLUDE -> included.add(g.id)
                            Filter.TriState.STATE_EXCLUDE -> excluded.add(g.id)
                        }
                    }

                    if (included.isNotEmpty()) {
                        directory = directory
                            .filter { included.any { t -> it.publishedStatus == t } }
                    }

                    if (excluded.isNotEmpty()) {
                        directory = directory
                            .filterNot { excluded.any { t -> it.publishedStatus == t } }
                    }
                }
                is SortFilter -> {
                    directory = when (filter.state?.index) {
                        0 -> directory.sortedBy { it.name }
                        1 -> directory.sortedBy { it.rank }
                        2 -> directory.sortedBy { MangaFunUtils.convertShortTime(it.createdAt) }
                        3 -> directory.sortedBy { MangaFunUtils.convertShortTime(it.updatedAt) }
                        else -> throw IllegalStateException("Unhandled sort option")
                    }

                    if (filter.state?.ascending != true) {
                        directory = directory.reversed()
                    }
                }
                else -> {}
            }
        }

        return parseDirectory(1)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val nextDataUrl = "$baseUrl/_next/data/$nextBuildId/title/$slug.json"

        return GET(nextDataUrl, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<NextPagePropsWrapperDto>()
            .pageProps
            .dehydratedState
            .queries
            .first()
            .state
            .data

        return json.decodeFromJsonElement<MangaDto>(data).toSManga()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<NextPagePropsWrapperDto>()
            .pageProps
            .dehydratedState
            .queries
            .first()
            .state
            .data

        val mangaData = json.decodeFromJsonElement<MangaDto>(data)
        return mangaData.chapters.map { it.toSChapter(mangaData.id, mangaData.name) }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/").substringBefore("-")

        return GET("$apiUrl/chapter/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val encoded = Base64.encode(response.body.bytes(), Base64.DEFAULT or Base64.NO_WRAP).toString(Charsets.UTF_8)
        val decoded = LZString.decompressFromBase64(encoded)
        val compressedJson = json.parseToJsonElement(decoded).jsonArray
        val decompressedJson = DecompressJson.decompress(compressedJson).jsonObject

        Log.d("MangaFun", Json.encodeToString(decompressedJson))

        return decompressedJson.jsonObject["p"]!!.jsonArray.mapIndexed { i, it ->
            Page(i, imageUrl = MangaFunUtils.getImageUrlFromHash(it.jsonArray[0].jsonPrimitive.content))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    private fun parseDirectory(page: Int): MangasPage {
        val endRange = min((page * 24), directory.size)
        val manga = directory.subList(((page - 1) * 24), endRange).map { it.toSManga() }
        val hasNextPage = endRange < directory.lastIndex

        return MangasPage(manga, hasNextPage)
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    companion object {
        internal const val PREFIX_ID_SEARCH = "id:"
        internal const val MANGAFUN_EPOCH = 1693473000
    }
}
