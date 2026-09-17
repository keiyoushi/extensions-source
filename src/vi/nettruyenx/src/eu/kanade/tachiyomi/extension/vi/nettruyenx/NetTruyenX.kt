package eu.kanade.tachiyomi.extension.vi.nettruyenx

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
abstract class NetTruyenX : WPComics() {
    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val popularPath = "truyen-tranh-hot"

    // Details
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = document.select("div.detail-content div div:nth-child(4)").joinToString("\n") { it.wholeText().trim() }
    }

    override suspend fun mangaUpdateParse(response: Response, manga: SManga, chapters: List<SChapter>): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/") // slug

        val updatedManga = mangaDetailsParse(response.asJsoup())

        val updatedChapters = run {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("Comic/Services/ComicService.asmx/ChapterList")
                .addQueryParameter("slug", slug)
                .build()
            val response = client.get(url)
            val json = response.parseAs<ChapterDTO>()
            json.data.map {
                SChapter.create().apply {
                    setUrlWithoutDomain("$baseUrl/truyen-tranh/$slug/${it.chapterSlug}")
                    name = it.chapterName
                    date_upload = dateFormatChapter.tryParseDateTime(it.updatedAt)
                }
            }
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    @Serializable
    class ChapterDTO(
        val data: List<Data> = emptyList(),
    )

    @Serializable
    class Data(
        @SerialName("chapter_name") val chapterName: String,
        @SerialName("chapter_slug") val chapterSlug: String,
        @SerialName("updated_at") val updatedAt: String,
    )

    private val dateFormatChapter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
}
