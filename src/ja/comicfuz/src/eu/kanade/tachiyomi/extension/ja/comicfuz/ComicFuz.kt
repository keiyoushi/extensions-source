package eu.kanade.tachiyomi.extension.ja.comicfuz

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.toRequestBodyProto
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class ComicFuz : KeiSource() {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/v1"
    private val cdnUrl get() = "https://img.$domain"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(ImageInterceptor)

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val payload = DayOfWeekRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            dayOfWeek = DayOfWeek.today(),
        ).toRequestBodyProto()

        val data = client.post("$apiUrl/mangas_by_day_of_week", Fetch.XHR, payload).parseAsProto<MangaListResponse>()
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

            val data = client.post("$apiUrl/search", Fetch.XHR, payload).parseAsProto<SearchResponse>()
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

            val data = client.post("$apiUrl/manga_list", Fetch.XHR, payload).parseAsProto<MangaListResponse>()
            val entries = data.mangas.map {
                it.toSManga(cdnUrl)
            }

            MangasPage(entries, false)
        }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = getMangaDetails(manga.url.substringAfterLast("/").toInt())
        val chapterList = data.chapterGroups.flatMap { group ->
            group.chapters.map { chapter ->
                chapter.toSChapter()
            }
        }

        return SMangaUpdate(data.toSManga(cdnUrl), chapterList)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != domain) return null
        val segments = url.pathSegments
        if (segments.size != 2 || segments[0] != "manga") return null
        val id = segments[1].toIntOrNull() ?: return null

        return getMangaDetails(id).toSManga(cdnUrl).apply { initialized = true }
    }

    private suspend fun getMangaDetails(mangaId: Int): MangaDetailsResponse {
        val payload = MangaDetailsRequest(
            deviceInfo = DeviceInfo(
                deviceType = DeviceType.BROWSER,
            ),
            mangaId = mangaId,
        ).toRequestBodyProto()

        return client.post("$apiUrl/manga_detail", Fetch.XHR, payload).parseAsProto<MangaDetailsResponse>()
    }

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

        val data = client.post("$apiUrl/manga_viewer", Fetch.XHR, payload).parseAsProto<MangaViewerResponse>()

        val pages = data.pages
            .filter { it.image?.isExtraPage == false }
            .mapNotNull { it.image }

        return pages.mapIndexed { idx, page ->
            val imageUrl = "$cdnUrl${page.imageUrl}".toHttpUrl().newBuilder().apply {
                if (page.encryptionKey.isNotEmpty() || page.iv.isNotEmpty()) {
                    addQueryParameter("key", page.encryptionKey)
                    addQueryParameter("iv", page.iv)
                }
            }.toString()

            Page(index = idx, imageUrl = imageUrl)
        }
    }
}
