package eu.kanade.tachiyomi.extension.ko.pandatoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("검색어가 있으면 필터는 적용되지 않습니다"),
    SortFilter(),
    RatingFilter(),
    GenreFilter(),
    StatusFilter(),
)

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String?>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "정렬",
        listOf(
            "실시간 랭킹" to "ranking",
            "최근 업데이트" to "updated",
            "인기작" to "popular",
            "새로 추가됨" to "new",
        ),
    )

// The site lists adult works apart from the others
class RatingFilter : SelectFilter("등급", listOf("일반" to null, "성인" to "adult"))

class GenreFilter :
    SelectFilter(
        "장르",
        listOf(
            "전체" to null,
            "액션" to "1",
            "모험" to "2",
            "코미디" to "3",
            "드라마" to "4",
            "판타지" to "5",
            "역사" to "13",
            "공포" to "6",
            "무협" to "15",
            "성인" to "16",
            "미스터리" to "7",
            "심리" to "14",
            "로맨스" to "8",
            "SF" to "9",
            "일상" to "10",
            "스포츠" to "11",
            "스릴러" to "12",
        ),
    )

class StatusFilter :
    SelectFilter(
        "상태",
        listOf(
            "전체" to null,
            "연재중" to "0",
            "완결" to "1",
            "휴재" to "2",
            "중단" to "3",
            "공개 예정" to "4",
        ),
    )
