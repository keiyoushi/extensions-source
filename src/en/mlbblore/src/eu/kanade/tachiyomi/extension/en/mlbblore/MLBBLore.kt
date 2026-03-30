package eu.kanade.tachiyomi.extension.en.mlbblore

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response

class MLBBLore : HttpSource() {

    override val name = "MLBB Lore Comics"
    override val baseUrl = "https://play.mobilelegends.com"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://api.mobilelegends.com"
    private val pageSize = 5

    @Serializable
    data class ApiListResponse(
        val data: List<AlbumEntry> = emptyList(),
    )

    @Serializable
    data class ApiDetailResponse(
        val data: AlbumDetail? = null,
    )

    @Serializable
    data class AlbumEntry(
        val id: Int = 0,
        val type: Int = 0,
        val title: String = "",
        @SerialName("hero_name") val heroName: String = "",
        val thumb: String = "",
    )

    @Serializable
    data class AlbumDetail(
        val id: Int = 0,
        val title: String = "",
        @SerialName("hero_name") val heroName: String = "",
        val thumb: String = "",
        @SerialName("share_content") val shareContent: String = "",
        @SerialName("comic_content") val comicContent: List<String> = emptyList(),
    )

    // Helpers

    private fun String.toAbsoluteUrl() = if (startsWith("//")) "https:$this" else this

    private fun formRequest(url: String, params: Map<String, String>): Request {
        val formBody = FormBody.Builder()
        params.forEach { (key, value) -> formBody.add(key, value) }
        return POST(url, body = formBody.build())
    }

    private fun detailRequest(id: String): Request = formRequest(
        "$apiUrl/lore/album/detail",
        mapOf(
            "id" to id,
            "lang" to "en",
            "token" to "",
        ),
    )

    override fun popularMangaRequest(page: Int): Request = formRequest(
        "$apiUrl/lore/album/list",
        mapOf(
            "type" to "3",
            "sort" to "3",
            "page" to page.toString(),
            "page_size" to "$pageSize",
            "lang" to "en",
            "token" to "",
        ),
    )

    override fun latestUpdatesRequest(page: Int): Request = formRequest(
        "$apiUrl/lore/album/list",
        mapOf(
            "type" to "3",
            "sort" to "1",
            "page" to page.toString(),
            "page_size" to "$pageSize",
            "lang" to "en",
            "token" to "",
        ),
    )

    private fun parseMangaListResponse(response: Response): MangasPage {
        val result = response.parseAs<ApiListResponse>()
        val mangas = result.data
            .filter { it.type == 3 }
            .map { entry ->
                SManga.create().apply {
                    url = entry.id.toString()
                    title = entry.title
                    author = entry.heroName.trim()
                    thumbnail_url = entry.thumb.toAbsoluteUrl()
                }
            }
        return MangasPage(mangas, result.data.size >= pageSize)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListResponse(response)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListResponse(response)

    // Manga details

    override fun mangaDetailsRequest(manga: SManga): Request = detailRequest(manga.url)

    override fun mangaDetailsParse(response: Response): SManga {
        val detail = response.parseAs<ApiDetailResponse>().data ?: return SManga.create()
        return SManga.create().apply {
            title = detail.title
            author = detail.heroName.trim()
            thumbnail_url = detail.thumb.toAbsoluteUrl()
            description = detail.shareContent
            status = SManga.COMPLETED
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request = detailRequest(manga.url)

    override fun chapterListParse(response: Response): List<SChapter> {
        val detail = response.parseAs<ApiDetailResponse>().data ?: return emptyList()
        return listOf(
            SChapter.create().apply {
                name = "Chapter 1"
                chapter_number = 1f
                url = detail.id.toString()
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request = detailRequest(chapter.url)

    override fun pageListParse(response: Response): List<Page> {
        val detail = response.parseAs<ApiDetailResponse>().data ?: return emptyList()
        return detail.comicContent.mapIndexed { index, raw ->
            Page(index, imageUrl = raw.toAbsoluteUrl())
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListResponse(response)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
