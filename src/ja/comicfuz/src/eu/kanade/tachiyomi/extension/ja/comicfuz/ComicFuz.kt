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

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(page, "", getFilterList())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val payload = DayOfWeekRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            dayOfWeek = DayOfWeek.today(),
        ).toRequestBody()

        return POST("$apiUrl/mangas_by_day_of_week", headers, payload)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListResponse>()
        val entries = data.mangas.map {
            it.toSManga(cdnUrl)
        }

        return MangasPage(entries, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tag = filters.filterIsInstance<TagFilter>().first()

        return if (query.isNotBlank() || tag.selected == null) {
            val payload = SearchRequest(
                deviceInfo = DeviceInfo(
                    deviceType = DeviceType.BROWSER,
                ),
                query = query.trim(),
                pageIndexOfMangas = page,
                pageIndexOfBooks = 1,
            ).toRequestBody()

            POST("$apiUrl/search#$page", headers, payload)
        } else {
            val payload = MangaListRequest(
                deviceInfo = DeviceInfo(
                    deviceType = DeviceType.BROWSER,
                ),
                tagId = tag.selected!!,
            ).toRequestBody()

            POST("$apiUrl/manga_list", headers, payload)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return if (response.request.url.pathSegments.last() == "search") {
            val data = response.parseAs<SearchResponse>()
            val page = response.request.url.fragment!!.toInt()
            val entries = data.mangas.map {
                it.toSManga(cdnUrl)
            }

            MangasPage(entries, data.pageCountOfMangas > page)
        } else {
            val data = response.parseAs<MangaListResponse>()
            val entries = data.mangas.map {
                it.toSManga(cdnUrl)
            }

            return MangasPage(entries, false)
        }
    }

    override fun getFilterList() = getFilters()

    override fun mangaDetailsRequest(manga: SManga): Request {
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
            viewerMode = ViewerMode(
                imageQuality = ImageQuality.HIGH,
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
