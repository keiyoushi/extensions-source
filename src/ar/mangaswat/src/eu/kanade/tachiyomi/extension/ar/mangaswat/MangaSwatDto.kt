package eu.kanade.tachiyomi.extension.ar.mangaswat

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// used for author or artist
@Serializable(with = PersonDtoSerializer::class)
internal class PersonDto(val name: String?)

internal object PersonDtoSerializer : KSerializer<PersonDto> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("PersonDto", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PersonDto) {
        value.name?.let { encoder.encodeString(it) }
    }

    override fun deserialize(decoder: Decoder): PersonDto {
        val json = (decoder as JsonDecoder).decodeJsonElement()
        val name = when (json) {
            is JsonPrimitive -> json.contentOrNull
            is JsonObject -> json["name"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
        return PersonDto(name)
    }
}

@Serializable
internal class MangaDetailsDto(
    private val title: String,
    private val story: String? = null,
    private val author: PersonDto? = null,
    private val artist: PersonDto? = null,
    private val genres: List<AttributesDto> = emptyList(),
    private val status: AttributesDto,
    private val poster: PosterDto,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDetailsDto.title
        description = story
        author = this@MangaDetailsDto.author?.name
        artist = this@MangaDetailsDto.artist?.name
        genre = genres.joinToString { it.name }
        status = when (this@MangaDetailsDto.status.name.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = poster.mediumUrl
    }
}

// used for genre and status.
@Serializable
internal class AttributesDto(
    internal val name: String,
)

@Serializable
internal class LatestUpdatesResponse(
    internal val results: List<LatestMangaDto> = emptyList(),
    private val next: String? = null,
) {
    fun hasNext(): Boolean = next != null
}

@Serializable
internal class LatestMangaDto(
    private val id: Int,
    private val slug: String,
    private val title: String,
    private val poster: PosterDto,
) {
    fun toSManga(): SManga = SManga.create().apply {
        this.title = this@LatestMangaDto.title
        this.thumbnail_url = poster.mediumUrl
        this.url = id.toString()
    }
}

@Serializable
internal class PosterDto(
    @SerialName("medium") internal val mediumUrl: String,
)

@Serializable
internal class ChapterListResponse(
    private val count: Int,
    internal val next: String? = null,
    internal val results: List<ChapterDto> = emptyList(),
)

@Serializable
internal class ChapterDto(
    private val id: Int,
    private val slug: String,
    private val chapter: String,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = chapter
        date_upload = MangaSwat.apiDateFormat.tryParse(createdAt)
        url = "/chapters/$id/$slug/"
    }
}

@Serializable
internal class PageListResponse(
    internal val images: List<PageDto>,
)

@Serializable
internal class PageDto(
    internal val image: String,
    internal val order: Int,
)
