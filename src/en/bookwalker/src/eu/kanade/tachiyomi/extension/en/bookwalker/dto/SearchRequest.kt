package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf

@Serializable
data class SearchRequestDto(
    @ProtoNumber(1) val limitOffset: LimitOffsetDto,
    @ProtoNumber(2) val query: String? = null,
    @ProtoNumber(3) val sort: SortDto,
    @ProtoNumber(4) val formats: List<SeriesFormat>,
    @ProtoNumber(5) val filters: List<FilterDto>,
    @ProtoNumber(6) val searchDomain: SearchPageTypeDto,
)

@Serializable
class LimitOffsetDto(
    @ProtoNumber(1) val limit: Int,
    @ProtoNumber(2) val offset: Int = 0,
)

@Serializable
class SortDto private constructor(
    // 0: Relevance
    // 1: A -> Z
    // 2: Newest
    // 3: Last Updated
    // 4: Popular
    // 5: Purchase date, ascending
    @ProtoNumber(1) private val sortMode: Int,
    // null: no
    // 1: yes
    @ProtoNumber(2) private val reverse: Int? = null,
) {
    fun reverse() = SortDto(sortMode, if (reverse == null) 1 else null)

    companion object {
        val RELEVANCE = SortDto(0)
        val ALPHABETICAL_ASC = SortDto(1)
        val NEWEST = SortDto(2)
        val LAST_UPDATED = SortDto(3)
        val POPULAR = SortDto(4)
        val LAST_PURCHASED = SortDto(5)
    }
}

@Serializable
class FilterDto(
    @ProtoNumber(1) val type: String,
    @ProtoNumber(3) val include: List<TagFilterDto>,
)

@Serializable
class TagFilterDto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(3) @EncodeDefault val mode: TagInclusionMode,
)

@JvmInline
@Serializable
value class TagInclusionMode(private val value: Int) {
    companion object {
        val INCLUDE = TagInclusionMode(2)
        val EXCLUDE = TagInclusionMode(3)
    }
}

@Serializable
class SearchPageTypeDto(@ProtoOneOf val domain: SearchPageType)

@Serializable
sealed interface SearchPageType {
    // Has to be a class, for some reason kotlin serialization doesn't like it when it's a singleton object instead.
    @Serializable
    class Browse : SearchPageType {
        @ProtoNumber(1)
        @EncodeDefault
        val unk = 1
    }

    @Serializable
    class Named(@ProtoNumber(2) private val name: String) : SearchPageType

    @Serializable
    class Local private constructor(@ProtoNumber(3) private val type: Int) : SearchPageType {
        companion object {
            val FAVORITES = Local(1)
            val AUTO_BUYING = Local(2)
            val PRE_ORDERED = Local(3)
            val PURCHASED = Local(4)
        }
    }
}
