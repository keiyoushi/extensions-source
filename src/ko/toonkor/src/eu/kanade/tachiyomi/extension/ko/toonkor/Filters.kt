package eu.kanade.tachiyomi.extension.ko.toonkor

import eu.kanade.tachiyomi.source.model.Filter

internal const val WEBTOONS_PATH = "/%EC%9B%B9%ED%88%B0" // /웹툰
internal const val MANGA_PATH = "/%EB%8B%A8%ED%96%89%EB%B3%B8" // /단행본
internal const val HENTAI_PATH = "/%EB%A7%9D%EA%B0%80" // /망가

internal const val ALL_STATUS_PATH = "/%EC%97%B0%EC%9E%AC" // /연재
internal const val COMPLETED_PATH = "/%EC%99%84%EA%B2%B0" // /완결

internal const val SORT_LATEST = ""
internal const val SORT_POPULAR = "?fil=%EC%9D%B8%EA%B8%B0" // ?fil=인기
internal const val SORT_TITLE = "?fil=%EC%A0%9C%EB%AA%A9" // ?fil=제목

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class TypeFilter :
    UriPartFilter(
        "Type",
        arrayOf(
            Pair("Webtoons", WEBTOONS_PATH),
            Pair("Manga", MANGA_PATH),
            Pair("Hentai", HENTAI_PATH),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ALL_STATUS_PATH),
            Pair("Completed", COMPLETED_PATH),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sort",
        arrayOf(
            Pair("Latest", SORT_LATEST),
            Pair("Popular", SORT_POPULAR),
            Pair("Title", SORT_TITLE),
        ),
    )
