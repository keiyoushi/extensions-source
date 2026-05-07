package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

inline fun <reified T> FilterList.firstInstanceOrNull(): T? = filterIsInstance<T>().firstOrNull()

// Genre Filter with TriState support (include/exclude)
class GenreFilter :
    Filter.Group<GenreTriState>(
        "장르 (탭하면: 무시→포함→제외)",
        genreList.map { GenreTriState(it) },
    )

class GenreTriState(val genre: String) : Filter.TriState(genre)

// Sort Filter
class SortFilter :
    Filter.Select<String>(
        "정렬",
        sortList.map { it.name }.toTypedArray(),
    )

// Status Filter
class StatusFilter :
    Filter.Select<String>(
        "상태",
        statusList.map { it.name }.toTypedArray(),
    )

// Filter option data class
data class FilterOption(val name: String, val value: String)

// Sort options
internal val sortList = listOf(
    FilterOption("최신순", "new"),
    FilterOption("북마크순", "bookmark"),
    FilterOption("조회순", "views"),
)

// Status options
internal val statusList = listOf(
    FilterOption("전체", ""),
    FilterOption("완결", "-end"),
)

// Genre list
internal val genreList = listOf(
    "순정",
    "판타지",
    "러브코미디",
    "드라마",
    "17",
    "학원",
    "라노벨",
    "개그",
    "액션",
    "백합",
    "SF",
    "일상",
    "이세계",
    "스릴러",
    "애니화",
    "전생",
    "스포츠",
    "TS",
    "소년",
    "먹방",
    "붕탁",
    "게임",
    "호러",
    "시대",
    "로맨스",
    "추리",
    "무협",
    "음악",
    "BL",
)

fun buildGenreParam(genreFilter: GenreFilter?): String? {
    if (genreFilter == null) return null

    val genres = genreFilter.state
        .filterIsInstance<GenreTriState>()
        .mapIndexed { index, triState ->
            when (triState.state) {
                Filter.TriState.STATE_INCLUDE -> genreList[index]
                Filter.TriState.STATE_EXCLUDE -> "-${genreList[index]}"
                else -> null
            }
        }
        .filterNotNull()

    return if (genres.isNotEmpty()) genres.joinToString(",") else null
}
