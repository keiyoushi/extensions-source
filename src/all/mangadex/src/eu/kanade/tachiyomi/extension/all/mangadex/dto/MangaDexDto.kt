package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

@Serializable(RelationshipSerializer::class)
sealed class RelationshipDto {
    abstract val id: String
    abstract val type: String
}

/** A relationship whose attributes weren't requested with `includes[]`, or that isn't used. */
@Serializable
class BareRelationshipDto(override val id: String, override val type: String) : RelationshipDto()

/** Another manga in a manga's relationships; [related] is how it's related, e.g. `sequel`. */
@Serializable
class RelatedMangaDto(
    override val id: String,
    override val type: String,
    val related: String,
) : RelationshipDto()

/** An author, artist or scanlation group. */
@Serializable
class NamedRelationshipDto(
    override val id: String,
    override val type: String,
    private val attributes: NameAttributesDto,
) : RelationshipDto() {
    val name get() = attributes.name
}

@Serializable
class NameAttributesDto(val name: String)

@Serializable
class UserRelationshipDto(
    override val id: String,
    override val type: String,
    private val attributes: UserAttributesDto,
) : RelationshipDto() {
    val username get() = attributes.username
}

@Serializable
class UserAttributesDto(val username: String)

@Serializable
class CoverRelationshipDto(
    override val id: String,
    override val type: String,
    private val attributes: CoverAttributesDto,
) : RelationshipDto() {
    val fileName get() = attributes.fileName
}

object RelationshipSerializer : JsonContentPolymorphicSerializer<RelationshipDto>(RelationshipDto::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RelationshipDto> {
        val obj = element.jsonObject
        if ("related" in obj) return RelatedMangaDto.serializer()
        if ("attributes" !in obj) return BareRelationshipDto.serializer()

        return when (obj["type"]!!.jsonPrimitive.content) {
            MDConstants.AUTHOR, MDConstants.ARTIST, MDConstants.SCANLATION_GROUP -> NamedRelationshipDto.serializer()
            MDConstants.USER -> UserRelationshipDto.serializer()
            MDConstants.COVER_ART -> CoverRelationshipDto.serializer()
            else -> BareRelationshipDto.serializer()
        }
    }
}

@Serializable
class MangaDto(
    val id: String,
    val attributes: MangaAttributesDto,
    val relationships: List<RelationshipDto>,
) {
    val coverFileName get() = relationships.firstInstanceOrNull<CoverRelationshipDto>()?.fileName
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
    val mangaId get() = relationships.first { it.type == MDConstants.MANGA }.id
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
    val mangaId get() = relationships.first { it.type == MDConstants.MANGA }.id
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
