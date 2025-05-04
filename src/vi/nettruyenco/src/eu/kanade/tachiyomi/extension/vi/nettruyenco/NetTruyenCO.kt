package eu.kanade.tachiyomi.extension.vi.nettruyenco

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale


class NetTruyenCO : WPComics(
    "NetTruyenCO (unoriginal)",
    "https://nettruyener.com",
    "vi",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault()),
    gmtOffset = null,
) {
    override val popularPath = "truyen-tranh-hot"

    // Override chapters

    @Serializable
    private data class ChapterDto(
        @SerialName("chapter_id") val chapterId: Int,
        @SerialName("chapter_name") val chapterName: String,
        @SerialName("chapter_slug") val chapterSlug: String,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("chapter_num") val chapterNum: Float,
    )

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override fun chapterListRequest(manga: SManga): Request {
        val slugAndId = manga.url.substringAfterLast("/") // e.g. "slug-12345"
        val comicId = slugAndId.substringAfterLast("-").toInt() // 12345
        val slug = slugAndId.substringBeforeLast("-") // "slug"
        val url = baseUrl.toHttpUrlOrNull()!!
            .newBuilder()
            .addPathSegments("Comic/Services/ComicService.asmx/ChapterList")
            .addQueryParameter("slug", slug)
            .addQueryParameter("comicId", comicId.toString())
            .build()
        return GET(url.toString(), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val bodyString = response.body?.string() ?: return emptyList()
        val rootElem = jsonParser.parseToJsonElement(bodyString).jsonObject

        val dataElem: JsonElement = when {
            "d" in rootElem -> {
                when (val d = rootElem["d"]!!) {
                    is JsonPrimitive -> jsonParser.parseToJsonElement(d.content).jsonObject["data"]!!
                    is JsonObject -> d["data"]!!
                    else -> return emptyList()
                }
            }
            "data" in rootElem -> rootElem["data"]!!
            else -> return emptyList()
        }

        val slug = response.request.url.queryParameter("slug") ?: ""
        val chaptersDto: List<ChapterDto> = jsonParser.decodeFromString(dataElem.toString())

        return chaptersDto.map { dto ->
            SChapter.create().apply {
                name = dto.chapterName
                setUrlWithoutDomain("/truyen-tranh/$slug/${dto.chapterSlug}/${dto.chapterId}")
                date_upload = chapterDateFormat.tryParse(dto.updatedAt) ?: 0L
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
                description = info.select("div.detail-content div.shortened").text() +
                    if (otherName.isNotBlank()) "\n\n ${intl["OTHER_NAME"]}: $otherName" else ""
                thumbnail_url = imageOrNull(info.select("div.col-image img").first()!!)
            }
        }
    }
}
