package eu.kanade.tachiyomi.extension.vi.nettruyenco

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NetTruyenCO : WPComics() {

    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    override val gmtOffset = null

    override val popularPath = "truyen-tranh-hot"

    // Override chapters

    // Data class mapping for a single chapter entry from the JSON endpoint
    @Serializable
    private class ChapterDto(
        @SerialName("chapter_id") val chapterId: Int,
        @SerialName("chapter_name") val chapterName: String,
        @SerialName("chapter_slug") val chapterSlug: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("chapter_num") val chapterNum: Float,
    )

    // Wrapper for the JSON response, containing a list of chapters
    @Serializable
    private class ChaptersData(
        val data: List<ChapterDto>,
    )

    override suspend fun mangaUpdateParse(response: Response, manga: SManga, chapters: List<SChapter>): SMangaUpdate {
        val slugAndId = manga.url.substringAfterLast("/") // e.g. "slug-12345"
        val comicId = slugAndId.substringAfterLast("-") // 12345
        val slug = slugAndId.substringBeforeLast("-") // "slug"

        val updatedManga = mangaDetailsParse(response.asJsoup())

        val updatedChapters = run {
            val url = baseUrl.toHttpUrl()
                .newBuilder()
                .addPathSegments("Comic/Services/ComicService.asmx/ChapterList")
                .addQueryParameter("slug", slug)
                .addQueryParameter("comicId", comicId)
                .build()
            val response = client.get(url)
            val chaptersDto = response.parseAs<ChaptersData>().data

            chaptersDto.map { dto ->
                SChapter.create().apply {
                    name = dto.chapterName
                    setUrlWithoutDomain("/truyen-tranh/$slug/${dto.chapterSlug}/${dto.chapterId}")
                    date_upload = dateFormat.tryParseDateTime(dto.updatedAt)
                    chapter_number = dto.chapterNum
                }
            }
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = document.select("div.detail-content div.shortened").flatMap { it.children() }
            .joinToString("\n\n") { it.wholeText().trim() }
    }
}
