package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

// Webtoon type filter (t2) — only on the webtoon browse page.
class TypeFilter :
    Filter.Select<String>(
        "분류",
        arrayOf(
            "전체",
            "일반",
            "BL",
            "성인",
        ),
    ),
    UrlPartFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        if (state != 0) {
            url.addQueryParameter("t2", state.toString())
        }
    }
}

// Day-of-week filter (t1) — only on the webtoon browse page.
class DayFilter :
    Filter.Select<String>(
        "요일",
        arrayOf(
            "전체",
            "월",
            "화",
            "수",
            "목",
            "금",
            "토",
            "일",
            "10",
        ),
    ),
    UrlPartFilter {
    private val paramValues = listOf("", "1", "2", "3", "4", "5", "6", "7", "10")

    override fun addToUrl(url: HttpUrl.Builder) {
        val v = paramValues[state]
        if (v.isNotEmpty()) {
            url.addQueryParameter("t1", v)
        }
    }
}

// Genre filter (t3) for the webtoon browse page.
class GenreFilter :
    Filter.Select<String>(
        "장르",
        arrayOf(
            "전체",
            "드라마",
            "판타지",
            "액션",
            "로맨스",
            "일상",
            "개그",
            "미스터리",
            "순정",
            "스포츠",
            "스릴러",
            "무협",
            "학원",
            "공포",
            "스토리",
        ),
    ),
    UrlPartFilter {
    private val paramValues = arrayOf(
        "",
        "드라마",
        "판타지",
        "액션",
        "로맨스",
        "일상",
        "개그",
        "미스터리",
        "순정",
        "스포츠",
        "스릴러",
        "무협",
        "학원",
        "공포",
        "스토리",
    )

    override fun addToUrl(url: HttpUrl.Builder) {
        val v = paramValues[state]
        if (v.isNotEmpty()) {
            url.addQueryParameter("t3", v)
        }
    }
}

// Genre filter (t3) for the comic browse page.
class ComicGenreFilter :
    Filter.Select<String>(
        "장르",
        arrayOf(
            "전체",
            "액션",
            "판타지",
            "로맨스",
            "드라마",
            "이세계",
            "전생",
            "무협",
            "일상",
            "일상+치유",
            "순정",
            "러브코미디",
            "개그",
            "학원",
            "스포츠",
            "미스터리",
            "추리",
            "스릴러",
            "공포",
            "호러",
            "도박",
            "역사",
            "시대",
            "게임",
            "SF",
            "요리",
            "먹방",
            "음악",
            "라노벨",
            "애니화",
            "BL",
            "백합",
            "성인",
            "붕탁",
            "TS",
            "여장",
        ),
    ),
    UrlPartFilter {
    private val paramValues = arrayOf(
        "",
        "액션",
        "판타지",
        "로맨스",
        "드라마",
        "이세계",
        "전생",
        "무협",
        "일상",
        "일상+치유",
        "순정",
        "러브코미디",
        "개그",
        "학원",
        "스포츠",
        "미스터리",
        "추리",
        "스릴러",
        "공포",
        "호러",
        "도박",
        "역사",
        "시대",
        "게임",
        "sf",
        "요리",
        "먹방",
        "음악",
        "라노벨",
        "애니화",
        "bl",
        "백합",
        "성인",
        "붕탁",
        "ts",
        "여장",
    )

    override fun addToUrl(url: HttpUrl.Builder) {
        val v = paramValues[state]
        if (v.isNotEmpty()) {
            url.addQueryParameter("t3", v)
        }
    }
}

// Sort filter (o): n=latest, r=new works, f=popular.
class SortFilter(default: Int = 0) :
    Filter.Select<String>(
        "정렬 기준",
        options.map { it.first }.toTypedArray(),
        default,
    ),
    UrlPartFilter {

    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter("o", options[state].second)
    }

    companion object {
        val options = listOf(
            "최신순" to "n",
            "신작순" to "r",
            "인기순" to "f",
        )
    }
}

val POPULAR = FilterList(SortFilter(2))
val LATEST = FilterList(SortFilter(0))
