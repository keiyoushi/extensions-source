package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.Serializable
import kotlin.Triple

@Serializable
data class FilterV2Dto(
    val id: Int? = null,
    val name: String? = null,
    val statements: MutableList<FilterStatementDto> = mutableListOf(),
    val combination: Int = 0, // FilterCombination = FilterCombination.And,
    val sortOptions: SortOptions = SortOptions(),
    val limitTo: Int = 0,
) {
    fun addStatement(comparison: FilterComparison, field: FilterField, value: String) {
        if (value.isNotBlank()) {
            statements.add(FilterStatementDto(comparison.type, field.type, value))
        }
    }
    fun addStatement(comparison: FilterComparison, field: FilterField, values: java.util.ArrayList<out Any>) {
        if (values.isNotEmpty()) {
            statements.add(FilterStatementDto(comparison.type, field.type, values.joinToString(",")))
        }
    }

    fun addContainsNotTriple(list: List<Triple<FilterField, java.util.ArrayList<out Any>, ArrayList<Int>>>) {
        list.map {
            addStatement(FilterComparison.Contains, it.first, it.second)
            addStatement(FilterComparison.NotContains, it.first, it.third)
        }
    }
    fun addPeople(list: List<Pair<FilterField, ArrayList<Int>>>) {
        list.map {
            addStatement(FilterComparison.MustContains, it.first, it.second)
        }
    }
}

@Serializable
data class FilterStatementDto(
    // todo: Create custom serializator for comparison and field and remove .type extension in Kavita.kt
    val comparison: Int,
    val field: Int,
    val value: String,

)

@Serializable
enum class SortFieldEnum(val type: Int) {
    SortName(1),
    CreatedDate(2),
    LastModifiedDate(3),
    LastChapterAdded(4),
    TimeToRead(5),
    ReleaseYear(6),
    ;

    companion object {
        private val map = SortFieldEnum.values().associateBy(SortFieldEnum::type)
        fun fromInt(type: Int) = map[type]
    }
}

@Serializable
data class SortOptions(
    var sortField: Int = SortFieldEnum.SortName.type,
    var isAscending: Boolean = true,
)

@Serializable
enum class FilterCombination {
    Or,
    And,
}

@Serializable
enum class FilterField(val type: Int) {
    Summary(0),
    SeriesName(1),
    PublicationStatus(2),
    Languages(3),
    AgeRating(4),
    UserRating(5),
    Tags(6),
    CollectionTags(7),
    Translators(8),
    Characters(9),
    Publisher(10),
    Editor(11),
    CoverArtist(12),
    Letterer(13),
    Colorist(14),
    Inker(15),
    Penciller(16),
    Writers(17),
    Genres(18),
    Libraries(19),
    ReadProgress(20),
    Formats(21),
    ReleaseYear(22),
    ReadTime(23),
    Path(24),
    FilePath(25),
}

@Serializable
enum class FilterComparison(val type: Int) {
    Equal(0),
    GreaterThan(1),
    GreaterThanEqual(2),
    LessThan(3),
    LessThanEqual(4),
    Contains(5),
    MustContains(6),
    Matches(7),
    NotContains(8),
    NotEqual(9),
    BeginsWith(10),
    EndsWith(11),
    IsBefore(12),
    IsAfter(13),
    IsInLast(14),
    IsNotInLast(15),
}
