package eu.kanade.tachiyomi.extension.ja.comicfuz

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import rx.Observable
import kotlin.math.min

class ComicFuz : HttpSource() {

    override val name = "COMIC FUZ"

    private val domain = "comic-fuz.com"
    override val baseUrl = "https://$domain"
    private val apiUrl = "https://api.$domain/v1"
    private val cdnUrl = "https://img.$domain"

    override val lang = "ja"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())

            if (!response.isSuccessful) {
                val exception = when (response.code) {
                    401 -> "Unauthorized"
                    402 -> "Payment Required"
                    else -> "HTTP error ${response.code}"
                }

                throw IOException(exception)
            }

            return@addNetworkInterceptor response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return fetchSearchManga(page, "", FilterList())
    }

    override fun popularMangaRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun popularMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        if (page == 1) {
            client.newCall(latestUpdatesRequest(page))
                .execute()
                .use(::latestResponseParse)
        }

        val entries = cachedList.subList(
            (page - 1) * 20,
            min(page * 20, cachedList.size),
        )

        return Observable.just(MangasPage(entries, page * 20 < cachedList.size))
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val payload = DayOfWeekRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            dayOfWeek = DayOfWeek.ALL,
        ).toRequestBody()

        return POST("$apiUrl/mangas_by_day_of_week", headers, payload)
    }

    private fun latestResponseParse(response: Response) {
        val data = response.parseAs<DayOfWeekResponse>()

        cachedList = data.mangas.map {
            it.toSManga(cdnUrl)
        }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    private var lastMangaPage = 0
    private var lastBookPage = 0

    private var cachedList: List<SManga> = emptyList()
    private var cachedHasNextPage = false
    private var useCached = false
    private var cachedPage = 1

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!useCached || page == 1) {
            client.newCall(searchMangaRequest(page, query, filters))
                .execute()
                .use(::searchResponseParse)
        }

        return Observable.just(getCached(cachedPage))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) {
            lastMangaPage = 1
            lastBookPage = 1
            cachedPage = 1
        }

        val payload = SearchRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            query = query.trim(),
            pageIndexOfMangas = lastMangaPage,
            pageIndexOfBooks = lastBookPage,
        ).toRequestBody()

        return POST("$apiUrl/search", headers, payload)
    }

    private fun searchResponseParse(response: Response) {
        val data = response.parseAs<SearchResponse>()

        cachedHasNextPage = false
        if (data.pageCountOfMangas > lastMangaPage) {
            lastMangaPage++
            cachedHasNextPage = true
        }
        if (data.pageCountOfBooks > lastBookPage) {
            lastBookPage++
            cachedHasNextPage = true
        }

        useCached = true

        cachedList = with(data) {
            buildList(mangas.size + books.size) {
                var i = 0
                var j = 0

                while (i < mangas.size || j < books.size) {
                    if (i < mangas.size) {
                        add(mangas[i].toSManga(cdnUrl))
                        i++
                    }
                    if (j < books.size) {
                        add(books[j].toSManga(cdnUrl))
                        j++
                    }
                }
            }
        }
    }

    private fun getCached(page: Int): MangasPage {
        val entries = cachedList.subList(
            (page - 1) * 20,
            min(page * 20, cachedList.size),
        )

        cachedPage++

        val cachedNextPage = page * 20 < cachedList.size

        val hasNextPage = if (!cachedNextPage && cachedHasNextPage) {
            useCached = false
            cachedPage = 1
            true
        } else {
            cachedNextPage
        }

        return MangasPage(entries, hasNextPage)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!manga.url.contains("/manga/")) {
            throw UnsupportedOperationException()
        }
        val payload = MangaDetailsRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            mangaId = manga.url.substringAfterLast("/").toInt(),
        ).toRequestBody()

        return POST("$apiUrl/manga_detail", headers, payload)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaDetailsResponse>()

        return data.toSManga(cdnUrl)
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaDetailsResponse>()

        return data.chapterGroups.flatMap { group ->
            group.chapters.map { chapter ->
                chapter.toSChapter()
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val payload = MangaViewerRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            chapterId = chapter.url.substringAfterLast("/").toInt(),
            useTicket = false,
            consumePoint = UserPoint(
                event = 0,
                paid = 0,
            ),
        ).toRequestBody()

        return POST("$apiUrl/manga_viewer", headers, payload)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<MangaViewerResponse>()

        val pages = data.pages
            .filter { it.image?.isExtraPage == false }
            .mapNotNull { it.image }

        return pages.mapIndexed { idx, page ->
            Page(
                index = idx,
                imageUrl = if (page.encryptionKey.isEmpty() && page.iv.isEmpty()) {
                    cdnUrl + page.imageUrl
                } else {
                    "$cdnUrl${page.imageUrl}".toHttpUrl().newBuilder()
                        .addQueryParameter("key", page.encryptionKey)
                        .addQueryParameter("iv", page.iv)
                        .toString()
                },
            )
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private inline fun <reified T> Response.parseAs(): T {
        return ProtoBuf.decodeFromByteArray(body.bytes())
    }

    private inline fun <reified T : Any> T.toRequestBody(): RequestBody {
        return ProtoBuf.encodeToByteArray(this)
            .toRequestBody("application/protobuf".toMediaType())
    }
}

private const val LATEST_DAY_PREF = "latest_day_pref"
