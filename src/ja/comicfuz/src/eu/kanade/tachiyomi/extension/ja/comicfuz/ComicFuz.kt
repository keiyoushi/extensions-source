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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

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
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        val payload = DayOfWeekRequest(
            deviceInfo = DeviceInfo(
                deviceType = 2,
            ),
            dayOfWeek = 0,
        ).let(ProtoBuf::encodeToByteArray)
            .toRequestBody()

        return POST("$apiUrl/mangas_by_day_of_week", headers, payload)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<DayOfWeekResponse>()

        val entries = data.mangas.map {
            SManga.create().apply {
                url = it.id.toString()
                title = it.title
                thumbnail_url = cdnUrl + it.cover
                description = it.description
            }
        }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw UnsupportedOperationException()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val payload = MangaDetailsRequest(
            deviceInfo = DeviceInfo(
                deviceType = 2,
            ),
            mangaId = manga.url.toInt(),
        ).let(ProtoBuf::encodeToByteArray)
            .toRequestBody()

        return POST("$apiUrl/manga_detail", headers, payload)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/manga/${manga.url}"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaDetailsResponse>()

        return SManga.create().apply {
            url = data.manga.id.toString()
            title = data.manga.title
            thumbnail_url = cdnUrl + data.manga.cover
            description = data.manga.description
            genre = data.tags.joinToString { it.name }
            author = data.authors.joinToString { it.author.name }
        }
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaDetailsResponse>()

        return data.chapters.flatMap { group ->
            group.chapters.map { chapter ->
                SChapter.create().apply {
                    url = chapter.id.toString()
                    name = chapter.title
                    date_upload = chapter.timestamp
                }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val payload = MangaViewerRequest(
            deviceInfo = DeviceInfo(
                deviceType = 2,
            ),
            chapterId = chapter.url.toInt(),
            useTicket = false,
            consumePoint = UserPoint(
                event = 0,
                paid = 0,
            ),
        ).let(ProtoBuf::encodeToByteArray)
            .toRequestBody()

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
}
