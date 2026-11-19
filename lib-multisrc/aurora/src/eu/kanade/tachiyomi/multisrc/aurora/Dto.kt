package eu.kanade.tachiyomi.multisrc.aurora

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.string
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class SeriesDto(
    @JsonNames("updates")
    val series: List<MangaDto>,
) {
    fun toSMangaList(): List<SManga> = series.map(MangaDto::toSManga)
}

@Serializable
class MangaDto(
    @JsonNames("name")
    val title: String,
    @JsonNames("image")
    val coverUrl: String?,
    @JsonNames("url")
    val slug: String,
    val type: String?,
    val alternateName: String?,
    val description: String?,
    val author: Value?,
    val genre: List<String> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        description = MarkdownDescription.Builder()
            .description(this@MangaDto.description)
            .altTitles(listOfNotNull(alternateName), "Títulos alternativos")
            .build()
        author = this@MangaDto.author?.name
        genre = this@MangaDto.genre.joinToString()
        val location = slug.toHttpUrlOrNull()
        url = location?.pathSegments?.last() ?: slug

        memo = buildJsonObject {
            put("slug", url)
            put("type", (location?.pathSegments?.first() ?: type)?.lowercase())
        }
    }
}

@Serializable
class Value(val name: String)

@Serializable
class ChapterListDto(
    @JsonNames("slug")
    val serieSlug: String,
    val chapters: List<ChapterDto>,
) {
    fun toSChapterList(manga: SManga) = chapters.map { it.toSChapter(manga, serieSlug) }
}

@Serializable
class ChapterDto(
    val id: String,
    val number: String,
    val releaseDate: String,
) {
    fun toSChapter(manga: SManga, serieSlug: String) = SChapter.create().apply {
        val type = manga.memo["type"]!!.string
        name = "Capítulo $number"
        date_upload = dateFormat.tryParseDate(releaseDate)
        chapter_number = number.toFloat()
        url = id
        memo = buildJsonObject {
            put("id", id)
            put("number", number)
            put("slug", serieSlug)
            put("type", type)
        }
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
    }
}

@Serializable
class PagesDto(
    val pages: List<ImageDto>,
) {
    suspend fun toPageList(decode: suspend (String) -> String) = pages.mapIndexed { index, image ->
        Page(index, imageUrl = decode(image.url))
    }
}

@Serializable
class ImageDto(
    val url: String,
)

@Serializable
class KeyDto(
    val v: Int,
    val e: Int,
    val k: String,
)

@Serializable
class GateStartDto(
    val returnTo: String,
)

@Serializable
class GateResponseDto(
    val data: GateDto,
)

@Serializable
class GateDto(
    val token: String,
    val gateUrl: String,
)
