package eu.kanade.tachiyomi.extension.ko.ddtoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class UriPartFilter(name: String, val param: String, private val options: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter(popular: Boolean = false) : UriPartFilter("정렬", "odtype", listOf("최신순" to "", "인기순" to "hits"), if (popular) 1 else 0)

class StatusFilter : UriPartFilter("상태", "sl", listOf("연재" to "", "완결" to "2"))

class GenreFilter :
    UriPartFilter(
        "장르",
        "type1",
        listOf(
            "전체" to "", "성인" to "6", "드라마" to "7", "판타지" to "8", "액션" to "9", "로맨스" to "10", "일상" to "11", "개그" to "12",
            "미스터리" to "13", "순정" to "14", "스포츠" to "15", "BL" to "16", "스릴러" to "17", "무협" to "18", "학원" to "19", "공포" to "20", "스토리" to "21",
        ),
    )

class DayFilter :
    UriPartFilter(
        "요일",
        "type2",
        listOf("전체" to "", "월" to "54", "화" to "55", "수" to "56", "목" to "57", "금" to "58", "토" to "59", "일" to "60", "10일" to "61"),
    )

class InitialFilter :
    UriPartFilter(
        "초성",
        "type3",
        listOf(
            "전체" to "", "ㄱ" to "1", "ㄴ" to "2", "ㄷ" to "3", "ㄹ" to "4", "ㅁ" to "5", "ㅂ" to "6", "ㅅ" to "7", "ㅇ" to "8",
            "ㅈ" to "9", "ㅊ" to "10", "ㅋ" to "11", "ㅌ" to "12", "ㅍ" to "13", "ㅎ" to "14", "A-Z" to "15", "0-9" to "16",
        ),
    )

fun getFilters() = FilterList(
    Filter.Header("검색어 입력 시 필터는 무시됩니다"),
    Filter.Header("완결 목록에서는 요일과 초성이 무시됩니다"),
    SortFilter(popular = true),
    StatusFilter(),
    GenreFilter(),
    DayFilter(),
    InitialFilter(),
)
