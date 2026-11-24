package eu.kanade.tachiyomi.extension.ko.marumaru

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    TypeFilter(),
    StatusFilter(),
    SortFilter(),
    FieldFilter(),
    Filter.Separator(),
    Filter.Header("검색어가 있으면 아래 필터와 신작순, 평점순 정렬은 적용되지 않습니다"),
    CategoryFilter(),
    WeekdayFilter(),
    GenreFilter(),
    PlatformFilter(),
)

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String?>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class TypeFilter : SelectFilter("종류", listOf("웹툰" to WEBTOON, "만화" to MANHWA))

class StatusFilter : Filter.Select<String>("상태", arrayOf("전체", "완결")) {
    val completed get() = state == 1
}

class SortFilter :
    SelectFilter(
        "정렬",
        listOf(
            "최신순" to "LATEST",
            "신작순" to "NEW",
            "북마크순" to "BOOKMARK",
            "조회순" to "VIEW",
            "평점순" to "RATING",
            "화수순" to "EPISODE",
        ),
    )

class FieldFilter : SelectFilter("검색 대상", listOf("제목" to null, "작가" to "author", "장르" to "genre"))

class CategoryFilter : Filter.Select<String>("분류", arrayOf("전체", "일반", "BL/GL", "성인")) {
    val param get() = when (state) {
        1 -> "blgl" to "false"
        2 -> "blgl" to "true"
        3 -> "adult" to "true"
        else -> null
    }
}

class WeekdayFilter :
    SelectFilter(
        "요일",
        listOf(
            "전체" to null,
            "월" to "MON",
            "화" to "TUE",
            "수" to "WED",
            "목" to "THU",
            "금" to "FRI",
            "토" to "SAT",
            "일" to "SUN",
        ),
    )

class GenreFilter : SelectFilter("장르", listOf("전체" to null) + GENRES.map { it to it })

class PlatformFilter : SelectFilter("플랫폼", listOf("전체" to null) + PLATFORMS.map { it to it })

// The webtoon genres, then the ones only the manhwa list offers
private val GENRES = listOf(
    "학원", "액션", "SF", "스토리", "판타지", "BL/백합", "개그", "드라마", "로맨스", "시대", "스포츠", "일상", "성인", "무협",
    "연애", "추리", "공포", "옴니버스", "에피소드", "소년", "기타", "고수위",
    "순정", "러브코미디", "17", "라노벨", "백합", "이세계", "스릴러", "애니화", "전생", "TS", "먹방", "붕탁", "게임",
    "호러", "음악", "BL",
)

private val PLATFORMS = listOf(
    "네이버", "카카오", "레진", "투믹스", "탑툰", "코미카", "배틀코믹스", "케이툰", "애니툰", "피너툰", "봄툰", "코미코", "리디북스", "기타",
)
