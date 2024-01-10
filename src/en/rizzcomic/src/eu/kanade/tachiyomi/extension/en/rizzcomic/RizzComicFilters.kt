package eu.kanade.tachiyomi.extension.en.rizzcomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.FormBody

interface FormBodyFilter {
    fun addFormParameter(form: FormBody.Builder)
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : FormBodyFilter, Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    abstract val formParameter: String
    override fun addFormParameter(form: FormBody.Builder) {
        form.add(formParameter, options[state].second)
    }
}

class SortFilter(defaultOrder: String? = null) : SelectFilter("Sort By", sort, defaultOrder) {
    override val formParameter = "OrderValue"
    companion object {
        private val sort = listOf(
            Pair("Default", "all"),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
        )

        val POPULAR = FilterList(StatusFilter(), TypeFilter(), SortFilter("popular"))
        val LATEST = FilterList(StatusFilter(), TypeFilter(), SortFilter("update"))
    }
}

class StatusFilter : SelectFilter("Status", status) {
    override val formParameter = "StatusValue"
    companion object {
        private val status = listOf(
            Pair("All", "all"),
            Pair("Ongoing", "ongoing"),
            Pair("Complete", "completed"),
            Pair("Hiatus", "hiatus"),
        )
    }
}

class TypeFilter : SelectFilter("Type", type) {
    override val formParameter = "TypeValue"
    companion object {
        private val type = listOf(
            Pair("All", "all"),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Comic", "Comic"),
        )
    }
}

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

class GenreFilter(
    genres: List<Pair<String, String>>,
) : FormBodyFilter, Filter.Group<CheckBoxFilter>(
    "Genre",
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    override fun addFormParameter(form: FormBody.Builder) {
        state.filter { it.state }.forEach {
            form.add("genres_checked[]", it.value)
        }
    }
}
