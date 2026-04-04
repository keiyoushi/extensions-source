package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class ChaptersRequestDto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val chapterType: ChapterType,
)

@Serializable
class ChaptersResponseDto(
    @ProtoNumber(1) val chapterType: ChapterType,
    @ProtoNumber(2) val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val readId: String,
    @ProtoNumber(3) val slug: String,
    @ProtoNumber(4) val thumbnail: ThumbnailInfoDto?,
    @ProtoNumber(5) val chapterNumber: ChapterNumberDto,
    @ProtoNumber(6) val title: String,
    @ProtoNumber(7) val releaseInfo: ReleaseInfoDto,
    // 0 = free
    @ProtoNumber(8) val currentPrice: Int = 0,
    @ProtoNumber(9) val regularPrice: Int = 0,
    // 0 = unowned, 1 = owned
    @ProtoNumber(10) private val _isOwned: Int,
) {
    val isOwned: Boolean
        get() = _isOwned == 1
}

@Serializable
class ChapterNumberDto(
    @ProtoNumber(1) val prefix: String,
    @ProtoNumber(2) val number: String,
)

@Serializable
class ReleaseInfoDto(
    // Unix time in seconds
    @ProtoNumber(1) val releaseDate: WrapperDto<Long>,
    @ProtoNumber(3) private val _isReleased: Int = 0,
) {
    val isReleased: Boolean
        get() = _isReleased == 1
}
