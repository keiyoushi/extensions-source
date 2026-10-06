package eu.kanade.tachiyomi.extension.ko.newxtoon

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    name: String,
    val param: String,
    private val options: Array<Pair<String, String?>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun toUriPart() = options[state].second
}

class SortFilter :
    UriPartFilter(
        "정렬",
        "sort",
        arrayOf(
            "인기순" to "popular",
            "최신순" to "latest",
        ),
    )

class CategoryFilter :
    UriPartFilter(
        "분류",
        "category",
        arrayOf(
            "전체" to null,
            "일반만화" to "일반만화",
            "BL·GL" to "BL·GL",
            "성인만화" to "성인",
        ),
    )

class GenreFilter :
    UriPartFilter(
        "장르",
        "genre",
        arrayOf(
            "전체" to null,
            "로맨스" to "1",
            "판타지" to "2",
            "액션" to "3",
            "드라마" to "4",
            "개그/코미디" to "6",
            "로맨스판타지" to "2739",
            "무협/사극" to "2743",
            "성장물" to "2753",
            "복수" to "2754",
            "빙의" to "2757",
            "달달물" to "2771",
            "먼치킨" to "2772",
            "소설원작" to "2774",
            "왕족/귀족" to "2777",
            "성장" to "2874",
            "능력녀" to "2902",
            "로맨틱코미디" to "2903",
            "다정남" to "2904",
            "능력남" to "2905",
            "완결로맨스" to "3266",
        ),
    )

class PlatformFilter :
    UriPartFilter(
        "플랫폼",
        "platform",
        arrayOf(
            "전체" to null,
            "카카오페이지" to "kakao-page",
            "네이버" to "naver",
            "레진코믹스" to "lezhin",
            "리디" to "ridi",
            "탑툰" to "toptoon",
            "봄툰" to "bomtoon",
            "미스터블루" to "mrblue",
            "투믹스" to "toomics",
            "피너툰" to "peanutoon",
            "코미코" to "comico",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "상태",
        "status",
        arrayOf(
            "전체" to null,
            "연재중" to "연재중",
            "완결" to "완결",
        ),
    )

class WeekdayFilter :
    UriPartFilter(
        "요일",
        "weekday",
        arrayOf(
            "전체" to null,
            "월" to "월",
            "화" to "화",
            "수" to "수",
            "목" to "목",
            "금" to "금",
            "토" to "토",
            "일" to "일",
        ),
    )
