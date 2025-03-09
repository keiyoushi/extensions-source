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
        val POPULAR = FilterList(StatusFilter(), TypeFilter(), SortFilter("popular"))
        val LATEST = FilterList(StatusFilter(), TypeFilter(), SortFilter("update"))
    }
}

private val sort = listOf(
    Pair("Default", "all"),
    Pair("A-Z", "title"),
    Pair("Z-A", "titlereverse"),
    Pair("Latest Update", "update"),
    Pair("Latest Added", "latest"),
    Pair("Popular", "popular"),
)

class StatusFilter : SelectFilter("Status", status) {
    override val formParameter = "StatusValue"
}

private val status = listOf(
    Pair("All", "all"),
    Pair("Ongoing", "ongoing"),
    Pair("Complete", "completed"),
    Pair("Hiatus", "hiatus"),
)

class TypeFilter : SelectFilter("Type", type) {
    override val formParameter = "TypeValue"
}

private val type = listOf(
    Pair("All", "all"),
    Pair("Manga", "manga"),
    Pair("Manhwa", "manhwa"),
    Pair("Manhua", "manhua"),
    Pair("Comic", "comic"),
)

class CheckBoxFilter(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

class GenreFilter : FormBodyFilter, Filter.Group<CheckBoxFilter>(
    "Genre",
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    override fun addFormParameter(form: FormBody.Builder) {
        state.filter { it.state }.forEach {
            form.add("genres_checked[]", it.value)
        }
    }
}

val genres = listOf(
    Pair("Abilities", "2"),
    Pair("Action", "3"),
    Pair("Adaptation", "4"),
    Pair("Adventure", "5"),
    Pair("Another Chance", "6"),
    Pair("Apocalypse", "7"),
    Pair("Based On A Novel", "8"),
    Pair("Cheat", "9"),
    Pair("Comedy", "10"),
    Pair("Conspiracy", "11"),
    Pair("Cultivation", "12"),
    Pair("Demon", "13"),
    Pair("Demon King", "14"),
    Pair("Dragon", "15"),
    Pair("Drama", "16"),
    Pair("Drop", "17"),
    Pair("Dungeon", "18"),
    Pair("Dungeons", "19"),
    Pair("Fantasy", "20"),
    Pair("Game", "21"),
    Pair("Genius", "22"),
    Pair("Ghosts", "23"),
    Pair("Harem", "24"),
    Pair("Hero", "25"),
    Pair("Hidden Identity", "26"),
    Pair("HighFantasy", "27"),
    Pair("Historical", "28"),
    Pair("Horror", "29"),
)
