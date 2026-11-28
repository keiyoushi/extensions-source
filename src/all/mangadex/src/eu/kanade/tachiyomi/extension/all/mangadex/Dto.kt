package eu.kanade.tachiyomi.extension.all.mangadex

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

const val MANGA = "manga"
const val COVER_ART = "cover_art"
const val SCANLATION_GROUP = "scanlation_group"
const val USER = "user"
const val AUTHOR = "author"
const val ARTIST = "artist"

@Serializable
class PagedDto<T>(
    val data: List<T>,
    val limit: Int,
    val offset: Int,
    val total: Int,
) {
    val hasNextPage: Boolean
        get() = limit + offset < total
}

@Serializable
class DataDto<T>(val data: T)

/** [related] is how a manga is related, e.g. `sequel`; [attributes] are only there when asked for with `includes[]`. */
@Serializable
class RelationshipDto(
    val id: String,
    val type: String,
    val related: String? = null,
    val attributes: RelationshipAttributesDto? = null,
)

@Serializable
class RelationshipAttributesDto(
    val name: String? = null,
    val username: String? = null,
    val fileName: String? = null,
)

@Serializable
class MangaDto(
    val id: String,
    val attributes: MangaAttributesDto,
    val relationships: List<RelationshipDto>,
) {
    val coverFileName get() = relationships.firstOrNull { it.type == COVER_ART }?.attributes?.fileName
}

@Serializable
class MangaAttributesDto(
    val title: LocalizedString,
    val altTitles: List<LocalizedString>,
    val description: LocalizedString,
    val originalLanguage: String,
    val lastVolume: String?,
    val lastChapter: String?,
    val contentRating: String,
    val publicationDemographic: String?,
    val status: String,
    val tags: List<TagDto>,
)

@Serializable
class TagDto(
    val id: String,
    private val attributes: TagAttributesDto,
) {
    val group get() = attributes.group
}

@Serializable
class TagAttributesDto(val group: String)

@Serializable
class ChapterDto(
    val id: String,
    val attributes: ChapterAttributesDto,
    val relationships: List<RelationshipDto>,
) {
    val mangaId get() = relationships.first { it.type == MANGA }.id
}

@Serializable
class ChapterAttributesDto(
    val title: String?,
    val volume: String?,
    val chapter: String?,
    val translatedLanguage: String,
    private val pages: Int,
    val publishAt: String,
    private val updatedAt: String,
    private val readableAt: String,
    private val externalUrl: String?,
    val isUnavailable: Boolean,
) {
    /**
     * An external chapter without pages, or one whose single page was replaced after it was
     * readable with a notice that the chapter was removed.
     */
    val isInvalid: Boolean
        get() = externalUrl != null && (pages == 0 || (pages == 1 && updatedAt != readableAt))
}

@Serializable
class CoverDto(
    val attributes: CoverAttributesDto,
    private val relationships: List<RelationshipDto>,
) {
    val mangaId get() = relationships.first { it.type == MANGA }.id
}

@Serializable
class CoverAttributesDto(
    val fileName: String,
    val locale: String?,
)

@Serializable
class AuthorDto(val id: String)

@Serializable
class CustomListDto(val relationships: List<RelationshipDto>)

@Serializable
class AtHomeDto(
    val baseUrl: String,
    val chapter: AtHomeChapterDto,
)

@Serializable
class AtHomeChapterDto(
    val hash: String,
    val data: List<String>,
    val dataSaver: List<String>,
)

@Serializable
class AggregateDto(
    @Serializable(AggregateVolumesSerializer::class)
    val volumes: Map<String, AggregateVolumeDto>,
)

@Serializable
class AggregateVolumeDto(
    @Serializable(AggregateChaptersSerializer::class)
    val chapters: Map<String, AggregateChapterDto>,
)

@Serializable
class AggregateChapterDto(val chapter: String)

typealias LocalizedString =
    @Serializable(LocalizedStringSerializer::class)
    Map<String, String>

/** The API sends maps keyed 0, 1, 2... (and empty ones) as JSON arrays. */
open class ObjectOrArraySerializer<V>(valueSerializer: KSerializer<V>) : JsonTransformingSerializer<Map<String, V>>(MapSerializer(String.serializer(), valueSerializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonObject(element.withIndex().associate { (i, value) -> i.toString() to value })
        else -> element
    }
}

object LocalizedStringSerializer : ObjectOrArraySerializer<String>(String.serializer())

object AggregateVolumesSerializer : ObjectOrArraySerializer<AggregateVolumeDto>(AggregateVolumeDto.serializer())

object AggregateChaptersSerializer : ObjectOrArraySerializer<AggregateChapterDto>(AggregateChapterDto.serializer())
