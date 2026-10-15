package eu.kanade.tachiyomi.extension.en.solarandsundry

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Request
import kotlin.time.Instant

private const val ACCEPT_IMAGE = "image/avif,image/webp,image/*,*/*"

private const val ARCHIVE_URL = "https://sas.ewanb.me"

@Source
abstract class SolarAndSundry : KeiSource() {

    override val supportsLatest = false

    @Serializable
    private class SasPage(
        @SerialName("page_number") val pageNumber: Int,
        @SerialName("image_url") val imageUrl: String,
        val name: String,
        @SerialName("published_at") val publishedAt: String,
    )

    private fun createManga(): SManga = SManga.create().apply {
        title = "Solar and Sundry"
        url = "/page"
        author = "Ewan Breakey"
        artist = author
        status = SManga.ONGOING
        description = "a sci-fi horror webcomic about life blooming against all odds"
        thumbnail_url = "https://imagedelivery.net/zthi1l8fKrUGB5ig08mq-Q/de292ba7-f164-4f43-ec17-1876a7a44600/public"
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(listOf(createManga()), false)

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(emptyList(), false)

    // Details & Chapters

    override fun getMangaUrl(manga: SManga): String = ARCHIVE_URL

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) createManga() else manga
        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val pages = client.get(baseUrl + manga.url).parseAs<List<SasPage>>()
        val chapterList = pages.map { page ->
            SChapter.create().apply {
                name = page.name
                setUrlWithoutDomain(baseUrl + "/page/" + page.pageNumber)
                chapter_number = page.pageNumber.toFloat()
                date_upload = Instant.tryParse(page.publishedAt)
            }
        }.reversed()

        return SMangaUpdate(details, chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String = ARCHIVE_URL + "/comic/" + chapter.chapter_number

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val page = client.get(baseUrl + chapter.url).parseAs<SasPage>()

        return listOf(Page(0, "", page.imageUrl))
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Accept", ACCEPT_IMAGE)
        .build()
}
