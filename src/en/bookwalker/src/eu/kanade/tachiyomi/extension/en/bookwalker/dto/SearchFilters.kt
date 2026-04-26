package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class SearchHeaderRequestDto

@Serializable
class SearchHeaderResponseDto(
    @ProtoNumber(1) val formats: List<SeriesFormat>,
    @ProtoNumber(2) val genres: SearchFilterOptionsDto,
)

@Serializable
class SearchFiltersRequestDto(
    @ProtoNumber(1) val searchRequest: SearchRequestDto,
)

@Serializable
class SearchFiltersResponseDto(
    @ProtoNumber(1) val filters: List<SearchFilterOptionsDto>,
)

@Serializable
class SearchFilterOptionsDto(
    @ProtoNumber(1) val filterType: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val options: List<FilterInfoDto>,
    @ProtoNumber(5) private val _hasMore: Int = 0,
) {
    val hasMore: Boolean
        get() = _hasMore == 1
}

@Serializable
class SearchFilterOptionsRequestDto(
    @ProtoNumber(1) val filterType: String,
    @ProtoNumber(2) val query: String = "",
    @ProtoNumber(3) val limitOffset: LimitOffsetDto,
    @ProtoNumber(5) val searchDomain: SearchPageTypeDto,
)

@Serializable
class SearchFilterOptionsResponseDto(
    @ProtoNumber(1) val countInfo: LimitOffsetCountDto,
    @ProtoNumber(2) val results: List<FilterInfoDto>,
)

@Serializable
class FilterInfoDto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
)
