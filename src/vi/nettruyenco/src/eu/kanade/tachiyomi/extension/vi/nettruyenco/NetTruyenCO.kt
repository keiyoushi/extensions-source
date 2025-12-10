package eu.kanade.tachiyomi.extension.vi.nettruyenco

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class NetTruyenCO : WPComics(
    "NetTruyenCO (unoriginal)",
    "https://nettruyenar.com",
    "vi",
    dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
    gmtOffset = null,
) {
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

    // Build and return the request to fetch all chapters in JSON form
    override fun chapterListRequest(manga: SManga): Request {
        val slugAndId = manga.url.substringAfterLast("/") // e.g. "slug-12345"
        val comicId = slugAndId.substringAfterLast("-") // 12345
        val slug = slugAndId.substringBeforeLast("-") // "slug"
        val url = baseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("Comic/Services/ComicService.asmx/ChapterList")
            .addQueryParameter("slug", slug)
            .addQueryParameter("comicId", comicId)
            .build()
        return GET(url, headers)
    }

    // Parse the JSON response into a list of SChapter objects
    override fun chapterListParse(response: Response): List<SChapter> {
        val chaptersDto = response.parseAs<ChaptersData>().data
        val slug = response.request.url.queryParameter("slug")!!

        return chaptersDto.map { dto ->
            SChapter.create().apply {
                name = dto.chapterName
                setUrlWithoutDomain("/truyen-tranh/$slug/${dto.chapterSlug}/${dto.chapterId}")
                date_upload = dateFormat.tryParse(dto.updatedAt)
                chapter_number = dto.chapterNum
            }
        }
    }

    override fun chapterListSelector(): String = throw UnsupportedOperationException()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = info.select("li.status p.col-xs-8").text().toStatus()
                genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
                val otherName = info.select("h2.other-name").text()
                description = info.select("div.detail-content div.shortened").flatMap { it.children() }.joinToString("\n\n") { it.wholeText().trim() } +
                    if (otherName.isNotBlank()) "\n\n ${intl["OTHER_NAME"]}: $otherName" else ""
                thumbnail_url = imageOrNull(info.select("div.col-image img").first()!!)
            }
        }
    }
}
