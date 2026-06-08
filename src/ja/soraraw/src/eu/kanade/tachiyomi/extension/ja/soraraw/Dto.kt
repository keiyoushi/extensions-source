package eu.kanade.tachiyomi.extension.ja.soraraw

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class NextDataDto(
    val buildId: String,
)

@Serializable
class NextDataWrapperDto<T>(
    val pageProps: PagePropsDto<T>,
)

@Serializable
class HtmlNextDataDto<T>(
    val props: NextDataWrapperDto<T>,
)

@Serializable
class PagePropsDto<T>(
    val data: T,
)

@Serializable
class MangaListDto(
    val list: List<MangaDto> = emptyList(),
)

@Serializable
class TopMangasDto(
    val mangas: List<MangaDto> = emptyList(),
)

@Serializable
class MangaDetailsDto(
    val manga: MangaDto,
)

@Serializable
class ChapterDetailsDto(
    val chapter: ChapterDto,
)

@Serializable
class MangaDto(
    val id: Int,
    val name: String? = null,
    val slug: String? = null,
    val img: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val summary: String? = null,
    val description: String? = null,
    val image: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val cover: String? = null,
    val thumbnail: String? = null,
    @SerialName("is_adult") val isAdult: String? = null,
    val mode: String? = null,
    val type: String? = null,
    val views: Int? = null,
    @SerialName("number_bookmark") val numberBookmark: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("c_published_at") val cPublishedAt: String? = null,
    @SerialName("c_published") val cPublished: String? = null,
    val rate: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("number_rate") val numberRate: Int? = null,
    val content: String? = null,
    val names: List<NameDto>? = null,
    @SerialName("alt_names") val altNames: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val genres: List<kotlinx.serialization.json.JsonElement>? = null,
) {
    fun toSManga(gMap: Map<String, String>): SManga {
        val mangaDto = this
        return SManga.create().apply {
            url = "/manga/${mangaDto.slug ?: ""}"
            title = mangaDto.name ?: ""
            description = mangaDto.description ?: mangaDto.summary

            val imgName = mangaDto.image ?: mangaDto.img
            thumbnail_url = mangaDto.thumbnail ?: if (imgName != null) {
                "https://i.mangaraw.lat/$imgName"
            } else {
                mangaDto.coverUrl ?: mangaDto.cover ?: ""
            }

            author = mangaDto.author
            artist = mangaDto.artist ?: mangaDto.author
            genre = mangaDto.genres?.mapNotNull { element ->
                if (element is JsonPrimitive) {
                    if (element.isString) {
                        element.content
                    } else {
                        gMap[element.content] ?: "Genre ${element.content}"
                    }
                } else if (element is JsonObject) {
                    element["name"]?.let { if (it is JsonPrimitive) it.content else null }
                } else {
                    null
                }
            }?.joinToString(", ")

            status = when (mangaDto.status?.lowercase() ?: mangaDto.type?.lowercase()) {
                "ongoing", "incomplete" -> SManga.ONGOING
                "completed", "complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class NameDto(
    val name: String,
)

@Serializable
class GenreDto(
    val name: String,
    val slug: String,
)

@Serializable
class ChapterDto(
    val id: Int,
    val name: kotlinx.serialization.json.JsonElement? = null,
    val title: String? = null,
    val order: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("manga_id") val mangaId: Int = -1,
    val uuid: String = "",
    @SerialName("c_slug") val cSlug: String? = null,
    val path: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val _b: String? = null,
    val _d: String? = null,
    val _p: String? = null,
    val _t: String? = null,
)

@Serializable
class CryptedPagesDto(
    val d: String,
)

@Serializable
class PageDataDto(
    val id: Int,
    val b: String? = null,
    val d: String? = null,
    val p: String? = null,
    val t: String? = null,
)
