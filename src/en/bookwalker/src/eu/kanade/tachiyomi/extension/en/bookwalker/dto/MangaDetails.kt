package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoPacked

@Serializable
class MangaDetailsRequestDto(
    @ProtoNumber(1) val id: String,
)

@Serializable
class MangaDetailsResponseDto(
    @ProtoNumber(1) val info: MangaInfoDto,
    // Tentative
    @ProtoNumber(5) private val _isMature: Int?,
    // Tentative, 2 = complete?
    @ProtoNumber(6) private val _status: Int?,
    @ProtoNumber(7) val tagline: String = "",
    @ProtoNumber(8) val description: String = "",
    @ProtoNumber(9) val metadata: List<MangaMetadataSectionDto>,
    @ProtoNumber(11) @ProtoPacked private val _chapterTypes: List<Int>,
) {
    val isMature: Boolean
        get() = _isMature == 1

    val status: Int
        get() = when (_status) {
            2 -> SManga.COMPLETED
            else -> SManga.ONGOING
        }

    val chapterTypes: List<ChapterType>
        get() = _chapterTypes.map { ChapterType(it) }
}

@Serializable
class MangaInfoDto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val slug: String,
    @ProtoNumber(4) val title: String,
    @ProtoNumber(8) val thumbnail: ThumbnailInfoDto?,
    @ProtoNumber(10) val tags: List<TagDto>,
)

@Serializable
class MangaMetadataSectionDto(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val contents: List<MangaMetadataDto>,
)

@Serializable
class MangaMetadataDto(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val id: String?,
    @ProtoNumber(4) val entity: MangaMetadataEntity?,
)

@Serializable
class MangaMetadataEntity(
    @ProtoNumber(1) val type: String,
    @ProtoNumber(3) val tag: MetadataTagDto,
)

@Serializable
class MetadataTagDto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val tagKind: TagKind,
)
