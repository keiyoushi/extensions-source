package eu.kanade.tachiyomi.extension.ja.rawz

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UriPartFilter {
    fun addQueryParameter(url: HttpUrl.Builder)
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

abstract class CheckBoxFilterGroup(
    name: String,
    genres: List<Pair<String, String>>,
) : UriPartFilter, Filter.Group<CheckBoxFilter>(
    name,
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    abstract val queryParameter: String
    override fun addQueryParameter(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            url.addQueryParameter(queryParameter, it.value)
        }
    }
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : UriPartFilter, Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    abstract val queryParameter: String
    override fun addQueryParameter(url: HttpUrl.Builder) {
        url.addQueryParameter(queryParameter, options[state].second)
    }
}

class TypeFilter : CheckBoxFilterGroup("タイプ", types) {
    override val queryParameter = "type[]"
    companion object {
        private val types = listOf(
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Oneshot", "oneshot"),
            Pair("Doujinshi", "doujinshi"),
        )
    }
}

class GenreFilter(genres: List<Pair<String, String>>) : CheckBoxFilterGroup("ジャンル", genres) {
    override val queryParameter = "taxonomy[]"
}

class StatusFilter : CheckBoxFilterGroup("ステータス", status) {
    override val queryParameter = "status[]"
    companion object {
        private val status = listOf(
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        )
    }
}

class ChapterNumFilter : SelectFilter("最小章", minChapNum) {
    override val queryParameter = "minchap"
    companion object {
        private val minChapNum = listOf(
            Pair(">= 1 chapters", "1"),
            Pair(">= 3 chapters", "3"),
            Pair(">= 5 chapters", "5"),
            Pair(">= 10 chapters", "10"),
            Pair(">= 20 chapters", "20"),
            Pair(">= 30 chapters", "30"),
            Pair(">= 50 chapters", "50"),
        )
    }
}

class SortFilter(default: String? = null) : SelectFilter("並び替え", sorts, default) {
    override val queryParameter = "order_by"
    companion object {
        private val sorts = listOf(
            Pair("Recently updated", "updated_at"),
            Pair("Recently added", "created_at"),
            Pair("Trending", "views"),
            Pair("Name A-Z", "name"),
        )

        val POPULAR = FilterList(SortFilter("views"))
        val LATEST = FilterList(SortFilter("updated_at"))
    }
}
