package eu.kanade.tachiyomi.extension.ko.nameetoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class StatusFilter : SelectFilter("상태", listOf("연재중", "완결").map { it to it })

class GenreFilter :
    SelectFilter(
        "장르",
        listOf("전체", "판타지", "액션", "개그", "미스터리", "로맨스", "드라마", "무협", "스포츠", "일상", "학원", "성인", "BL", "SF", "시대극", "기타").map { it to it },
    )

class DayFilter :
    SelectFilter(
        "요일",
        listOf("전체" to null, "월" to "1", "화" to "2", "수" to "3", "목" to "4", "금" to "5", "토" to "6", "일" to "0", "열흘" to "7"),
    )

fun getFilters() = FilterList(
    Filter.Header("검색어 입력 시 필터는 무시됩니다"),
    Filter.Header("요일을 고르면 장르는 무시됩니다"),
    StatusFilter(),
    GenreFilter(),
    DayFilter(),
)
