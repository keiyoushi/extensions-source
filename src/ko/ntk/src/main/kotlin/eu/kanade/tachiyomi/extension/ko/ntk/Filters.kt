package eu.kanade.tachiyomi.extension.ko.ntk

import eu.kanade.tachiyomi.source.model.Filter

data class FilterOption(val name: String, val value: String)

internal val sortList = listOf(
    FilterOption("최신순", "new"),
    FilterOption("북마크순", "bookmark"),
    FilterOption("조회순", "views"),
)

class GenreTriState(val genre: String) : Filter.TriState(genre)

class SortFilter : Filter.Select<String>("정렬", sortList.map { it.name }.toTypedArray())
class StatusFilter : Filter.Select<String>("상태", statusList.map { it.name }.toTypedArray())
class GenreFilter : Filter.Group<GenreTriState>("장르", genreList.map { GenreTriState(it) })

internal val statusList = listOf(
    FilterOption("전체", ""),
    FilterOption("완결", "-end"),
)

internal val genreList = listOf(
    "순정", "판타지", "러브코미디", "드라마", "17", "학원", "라노벨", "개그", "액션", "백합", "SF",
    "일상", "이세계", "스릴러", "애니화", "전생", "스포츠", "TS", "소년", "먹방", "붕탁", "게임",
    "호러", "시대", "로맨스", "추리", "무협", "음악", "BL",
)

fun buildGenreParam(genreFilter: GenreFilter?): String? {
    if (genreFilter == null) return null
    val genres = genreFilter.state
        .filterIsInstance<GenreTriState>()
        .mapIndexedNotNull { index, triState ->
            when (triState.state) {
                Filter.TriState.STATE_INCLUDE -> genreList[index]
                Filter.TriState.STATE_EXCLUDE -> "-${genreList[index]}"
                else -> null
            }
        }
    return if (genres.isNotEmpty()) genres.joinToString(",") else null
}

class WtSortFilter : Filter.Select<String>("정렬", sortList.map { it.name }.toTypedArray())
class WtStatusFilter : Filter.Select<String>("상태", wtStatusList.map { it.name }.toTypedArray())
class WtCategoryFilter : Filter.Select<String>("분류", wtCatList.map { it.name }.toTypedArray())
class WtDayFilter : Filter.Select<String>("요일", wtDayList.map { it.name }.toTypedArray())
class WtGenreFilter : Filter.Group<GenreTriState>("장르", wtGenreList.map { GenreTriState(it) })

internal val wtStatusList = listOf(
    FilterOption("연재중", "ing"),
    FilterOption("완결  (※ 장르 필터만 적용됩니다)", "end"),
)

internal val wtCatList = listOf(
    FilterOption("전체", ""),
    FilterOption("일반웹툰", "normal"),
    FilterOption("BL/GL", "bl"),
    FilterOption("성인웹툰", "adult"),
)

internal val wtDayList = listOf(
    FilterOption("전체", ""),
    FilterOption("월", "월"),
    FilterOption("화", "화"),
    FilterOption("수", "수"),
    FilterOption("목", "목"),
    FilterOption("금", "금"),
    FilterOption("토", "토"),
    FilterOption("일", "일"),
)

internal val wtGenreList = listOf(
    "순정", "판타지", "러브코미디", "드라마", "17", "학원", "라노벨", "개그", "액션", "백합", "SF",
    "일상", "이세계", "스릴러", "애니화", "전생", "스포츠", "TS", "소년", "먹방", "붕탁", "게임",
    "호러", "시대", "로맨스", "추리", "무협", "음악", "BL",
)

fun buildWtGenreParam(genreFilter: WtGenreFilter?): String? {
    if (genreFilter == null) return null
    val tags = genreFilter.state
        .filterIsInstance<GenreTriState>()
        .mapIndexedNotNull { index, triState ->
            when (triState.state) {
                Filter.TriState.STATE_INCLUDE -> index.toString()
                Filter.TriState.STATE_EXCLUDE -> "-$index"
                else -> null
            }
        }
    return if (tags.isNotEmpty()) tags.joinToString(",") else null
}
