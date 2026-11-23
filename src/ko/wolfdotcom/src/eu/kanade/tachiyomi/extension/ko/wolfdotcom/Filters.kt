package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import java.net.URLEncoder

interface UrlPartFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

@Serializable
class FilterRow(
    val param: String,
    val options: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val value: String,
)

class RowFilter(
    private val row: FilterRow,
) : Filter.Select<String>(
    ROW_NAMES[row.param] ?: row.param,
    row.options.map { it.name }.toTypedArray(),
),
    UrlPartFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val value = row.options[state].value
        if (value.isNotEmpty()) {
            // the site only understands EUC-KR encoded query values
            url.addEncodedQueryParameter(row.param, URLEncoder.encode(value, "EUC-KR"))
        }
    }

    companion object {
        private val ROW_NAMES = mapOf(
            "t1" to "요일",
            "t2" to "분류",
            "t3" to "장르",
        )
    }
}

class StatusFilter : Filter.Select<String>("상태", arrayOf("연재", "완결"))

class SortFilter(
    private val options: List<Pair<String, String>>,
    default: String = options.first().second,
) : Filter.Select<String>(
    "정렬 기준",
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == default },
),
    UrlPartFilter {

    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter("o", options[state].second)
    }
}

// The old template filters by one day, genre or initial at a time
class CategoryFilter :
    Filter.Select<String>("필터", CATEGORIES.map { it.name }.toTypedArray()),
    UrlPartFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        val category = CATEGORIES[state]
        category.type?.let { url.addQueryParameter("type1", it) }
        // the site only understands EUC-KR encoded query values
        category.value?.let { url.addEncodedQueryParameter("type2", URLEncoder.encode(it, "EUC-KR")) }
    }

    private class Category(val name: String, val type: String? = null, val value: String? = null)

    companion object {
        private val CATEGORIES = buildList {
            add(Category("전체"))
            listOf("최신" to "recent", "월" to "1", "화" to "2", "수" to "3", "목" to "4", "금" to "5", "토" to "6", "일" to "7", "열흘" to "10")
                .forEach { (name, value) -> add(Category("요일별: $name", "day", value)) }
            add(Category("장르별: 성인", "genre"))
            listOf("드라마", "판타지", "액션", "로맨스", "일상", "개그", "미스터리", "순정", "스포츠", "BL", "스릴러", "무협", "학원", "공포", "스토리")
                .forEach { add(Category("장르별: $it", "genre", it)) }
            (listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ").map { it to it } + listOf("A-Z" to "a", "0-9" to "0"))
                .forEach { (name, value) -> add(Category("작품별: $name", "alphabet", value)) }
        }
    }
}
