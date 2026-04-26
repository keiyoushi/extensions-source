package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class SearchResponseDto(
    @ProtoNumber(1) val countInfo: LimitOffsetCountDto,
    @ProtoNumber(3) val results: WrapperDto<List<WrapperDto<MangaInfoDto>>>,
)

@Serializable
class LimitOffsetCountDto(
    @ProtoNumber(1) val limit: Int,
    @ProtoNumber(2) val offset: Int = 0,
    @ProtoNumber(3) val totalCount: Int,
)
