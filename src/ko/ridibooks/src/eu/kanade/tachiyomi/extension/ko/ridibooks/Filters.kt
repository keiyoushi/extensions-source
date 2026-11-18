package eu.kanade.tachiyomi.extension.ko.ridibooks

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class CategoryFilter :
    UriPartFilter(
        "카테고리",
        arrayOf(
            "웹툰" to "1600",
            "만화 연재" to "6100",
            "만화 e북" to "1500",
            "BL 웹툰" to "4250",
            "BL 만화 e북" to "4200",
        ),
    )

class SortFilter :
    UriPartFilter(
        "정렬 (검색어 없을 때)",
        arrayOf(
            "인기순" to "popular",
            "최신순" to "recent",
            "평점순" to "rating",
            "리뷰 많은순" to "review",
        ),
    )
