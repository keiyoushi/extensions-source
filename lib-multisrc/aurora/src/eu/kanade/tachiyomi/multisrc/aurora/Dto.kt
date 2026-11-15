package eu.kanade.tachiyomi.multisrc.aurora

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
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
    var type: String? = null,
    val description: String? = null,
    val author: Value? = null,
    val genre: List<String> = emptyList(),
) {
    fun String.isUrl() = toHttpUrlOrNull() != null
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        description = this@MangaDto.description
        author = this@MangaDto.author?.name
        genre = this@MangaDto.genre.joinToString()
        url = when {
            slug.isUrl() -> {
                val location = slug.toHttpUrlOrNull()!!
                type = location.pathSegments.first()
                location.pathSegments.last()
            }
            else -> slug
        }

        memo = buildJsonObject {
            put("slug", url)
            put("type", type?.lowercase())
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
        private val dateFormat = DateTimeFormatter.ofPattern("dd 'de' MMM 'de' yyyy", Locale("pt", "BR"))
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
    val k: String,
)
