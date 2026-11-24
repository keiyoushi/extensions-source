package eu.kanade.tachiyomi.extension.ko.jjaptoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class UriPartFilter(name: String, val param: String, private val options: List<Pair<String, String?>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter(popular: Boolean = false) : UriPartFilter("정렬", "selectedSort", listOf("최신 업데이트" to "latest", "인기" to "popular"), if (popular) 1 else 0)

class TypeFilter : UriPartFilter("분류", "selectedType", listOf("전체" to null, "일반" to "general", "성인" to "adult", "BL" to "bl", "완결" to "completed"))

class ScheduleFilter :
    UriPartFilter(
        "요일",
        "selectedSchedule",
        listOf("전체" to null, "월" to "monday", "화" to "tuesday", "수" to "wednesday", "목" to "thursday", "금" to "friday", "토" to "saturday", "일" to "sunday"),
    )

class CategoryFilter :
    UriPartFilter(
        "장르",
        "selectedCategories",
        listOf(
            "전체" to null, "액션" to "1", "일상" to "3", "BL/백합" to "10", "로맨스" to "4", "소년" to "22", "개그/코미디" to "11", "학원" to "6",
            "SF" to "7", "스토리" to "8", "판타지" to "9", "연애/순정" to "12", "드라마" to "13", "시대극" to "14", "스포츠" to "15",
            "추리/미스터리" to "16", "공포/스릴러" to "17", "성인" to "18", "옴니버스" to "19", "에피소드" to "20", "무협" to "21", "기타" to "23",
            "SF/판타지" to "2", "개그" to "5",
        ),
    )

fun getFilters() = FilterList(
    Filter.Header("검색어 입력 시 필터는 무시됩니다"),
    SortFilter(popular = true),
    TypeFilter(),
    ScheduleFilter(),
    CategoryFilter(),
)
