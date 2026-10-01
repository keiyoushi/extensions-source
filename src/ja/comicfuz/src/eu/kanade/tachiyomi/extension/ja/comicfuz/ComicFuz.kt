package eu.kanade.tachiyomi.extension.ja.comicfuz

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.toRequestBodyProto
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.IOException

@Source
abstract class ComicFuz : KeiSource() {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/v1"
    private val cdnUrl get() = "https://img.$domain"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(ImageInterceptor)
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

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val payload = DayOfWeekRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            dayOfWeek = DayOfWeek.today(),
        ).toRequestBodyProto()

        val data = client.post("$apiUrl/mangas_by_day_of_week", payload).parseAsProto<MangaListResponse>()
        val entries = data.mangas.map {
            it.toSManga(cdnUrl)
        }

        return MangasPage(entries, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tag = filters.firstInstance<TagFilter>()

        return if (query.isNotBlank() || tag.selected == null) {
            val payload = SearchRequest(
                deviceInfo = DeviceInfo(
                    deviceType = DeviceType.BROWSER,
                ),
                query = query.trim(),
                pageIndexOfMangas = page,
                pageIndexOfBooks = 1,
            ).toRequestBodyProto()

            val data = client.post("$apiUrl/search", payload).parseAsProto<SearchResponse>()
            val entries = data.mangas.map {
                it.toSManga(cdnUrl)
            }

            MangasPage(entries, data.pageCountOfMangas > page)
        } else {
            val payload = MangaListRequest(
                deviceInfo = DeviceInfo(
                    deviceType = DeviceType.BROWSER,
                ),
                tagId = tag.selected!!,
            ).toRequestBodyProto()

            val data = client.post("$apiUrl/manga_list", payload).parseAsProto<MangaListResponse>()
            val entries = data.mangas.map {
                it.toSManga(cdnUrl)
            }

            MangasPage(entries, false)
        }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val payload = MangaDetailsRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            mangaId = manga.url.substringAfterLast("/").toInt(),
        ).toRequestBodyProto()

        val data = client.post("$apiUrl/manga_detail", payload).parseAsProto<MangaDetailsResponse>()
        val chapterList = data.chapterGroups.flatMap { group ->
            group.chapters.map { chapter ->
                chapter.toSChapter()
            }
        }

        return SMangaUpdate(data.toSManga(cdnUrl), chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
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
        ).toRequestBodyProto()

        val data = client.post("$apiUrl/manga_viewer", payload).parseAsProto<MangaViewerResponse>()

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
}
