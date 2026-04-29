package eu.kanade.tachiyomi.extension.ko.toon11

import eu.kanade.tachiyomi.source.model.Filter

class SelectFilterOption(val name: String, val value: String)

abstract class SelectFilter(
    name: String,
    private val options: List<SelectFilterOption>,
    default: Int = 0,
) : Filter.Select<String>(name, options.map { it.name }.toTypedArray(), default) {
    val selected: String
        get() = options[state].value
}

class SortFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Sort", options, default)
class StatusFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Status", options, default)
class GenreFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Genre", options, default)

internal val sortList = listOf(
    SelectFilterOption("인기만화", "/bbs/board.php?bo_table=toon_c"),
    SelectFilterOption("최신만화", "/bbs/board.php?bo_table=toon_c&tablename=최신만화&type=upd"),
)

internal val statusList = listOf(
    SelectFilterOption("전체", "0"),
    SelectFilterOption("완결", "1"),
)

internal val genreList = listOf(
    SelectFilterOption("전체", ""),
    SelectFilterOption("SF", "SF"),
    SelectFilterOption("무협", "무협"),
    SelectFilterOption("TS", "TS"),
    SelectFilterOption("개그", "개그"),
    SelectFilterOption("드라마", "드라마"),
    SelectFilterOption("러브코미디", "러브코미디"),
    SelectFilterOption("먹방", "먹방"),
    SelectFilterOption("백합", "백합"),
    SelectFilterOption("붕탁", "붕탁"),
    SelectFilterOption("스릴러", "스릴러"),
    SelectFilterOption("스포츠", "스포츠"),
    SelectFilterOption("시대", "시대"),
    SelectFilterOption("액션", "액션"),
    SelectFilterOption("순정", "순정"),
    SelectFilterOption("일상+치유", "일상%2B치유"),
    SelectFilterOption("추리", "추리"),
    SelectFilterOption("판타지", "판타지"),
    SelectFilterOption("학원", "학원"),
    SelectFilterOption("호러", "호러"),
    SelectFilterOption("BL", "BL"),
    SelectFilterOption("17", "17"),
    SelectFilterOption("이세계", "이세계"),
    SelectFilterOption("전생", "전생"),
    SelectFilterOption("라노벨", "라노벨"),
    SelectFilterOption("애니화", "애니화"),
    SelectFilterOption("TL", "TL"),
)
