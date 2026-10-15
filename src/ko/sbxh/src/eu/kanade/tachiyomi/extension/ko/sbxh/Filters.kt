package eu.kanade.tachiyomi.extension.ko.sbxh

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String?>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class Listing(
    val type: String,
    val status: String,
)

class ListingFilter :
    Filter.Select<String>(
        "목록",
        arrayOf("연재중 웹툰", "완결 웹툰", "연재중 만화", "완결 만화"),
    ) {
    val selected get() = listings[state]

    companion object {
        val listings = arrayOf(
            Listing("webtoon", "ongoing"),
            Listing("webtoon", "completed"),
            Listing("manhwa", "ongoing"),
            Listing("manhwa", "completed"),
        )
    }
}

class SortFilter :
    SelectFilter(
        "정렬",
        arrayOf(
            "최신순" to "new",
            "신작순" to "fresh",
            "북마크순" to "hot",
            "조회순" to "views",
            "평점순" to "rating",
            "화수순" to "episodes",
        ),
    )

class CategoryFilter :
    SelectFilter(
        "분류 (연재중 웹툰)",
        arrayOf(
            "전체" to "all",
            "일반웹툰" to "normal",
            "BL/GL" to "bl",
            "성인웹툰" to "adult",
        ),
    )

class DayFilter :
    SelectFilter(
        "요일 (연재중 웹툰)",
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

class WebtoonGenreFilter :
    SelectFilter(
        "장르 (웹툰)",
        arrayOf(
            "전체" to null,
            "학원" to "1",
            "액션" to "2",
            "SF" to "3",
            "스토리" to "4",
            "판타지" to "5",
            "BL/백합" to "6",
            "개그/코미디" to "7",
            "연애/순정" to "8",
            "드라마" to "9",
            "로맨스" to "10",
            "시대" to "11",
            "스포츠" to "12",
            "일상" to "13",
            "추리/미스터리" to "14",
            "공포/스릴러" to "15",
            "성인" to "16",
            "무협" to "19",
            "소년" to "20",
        ),
    )

class PlatformFilter :
    SelectFilter(
        "플랫폼 (웹툰)",
        arrayOf(
            "전체" to null,
            "네이버" to "1",
            "다음" to "2",
            "카카오" to "3",
            "레진" to "4",
            "투믹스" to "5",
            "탑툰" to "6",
            "코미카" to "7",
            "배틀코믹스" to "8",
            "코믹GT" to "9",
            "케이툰" to "10",
            "애니툰" to "11",
            "폭스툰" to "12",
            "피너툰" to "13",
            "봄툰" to "14",
            "코미코" to "15",
            "무툰" to "16",
            "리디북스" to "18",
            "기타" to "99",
        ),
    )

class MangaGenreFilter :
    SelectFilter(
        "장르 (만화)",
        arrayOf<Pair<String, String?>>("전체" to null) + arrayOf(
            "순정", "판타지", "러브코미디", "드라마", "17", "학원", "라노벨", "개그", "액션", "백합",
            "일상", "SF", "이세계", "스릴러", "애니화", "전생", "스포츠", "TS", "소년", "먹방",
            "붕탁", "게임", "호러", "시대", "로맨스", "추리", "음악", "무협", "BL",
        ).map { it to it },
    )

class SearchKindFilter :
    SelectFilter(
        "검색 대상",
        arrayOf(
            "전체" to null,
            "웹툰" to "webtoon",
            "만화" to "manhwa",
        ),
    )
