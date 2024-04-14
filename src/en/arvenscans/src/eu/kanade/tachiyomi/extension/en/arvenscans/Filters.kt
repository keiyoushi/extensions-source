package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlPartFilter {
    fun addUrlParameter(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val urlParameter: String,
    private val options: List<Pair<String, String>>,
) : UrlPartFilter, Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        url.addQueryParameter(urlParameter, options[state].second)
    }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    private val urlParameter: String,
    options: List<Pair<String, String>>,
) : UrlPartFilter, Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    override fun addUrlParameter(url: HttpUrl.Builder) {
        val checked = state.filter { it.state }.map { it.value }

        if (checked.isNotEmpty()) {
            url.addQueryParameter(urlParameter, checked.joinToString(","))
        }
    }
}

class StatusFilter : SelectFilter(
    "Status",
    "seriesStatus",
    listOf(
        Pair("", ""),
        Pair("Ongoing", "ONGOING"),
        Pair("Completed", "COMPLETED"),
        Pair("Cancelled", "CANCELLED"),
        Pair("Dropped", "DROPPED"),
        Pair("Mass Released", "MASS_RELEASED"),
        Pair("Coming Soon", "COMING_SOON"),
    ),
)

class TypeFilter : SelectFilter(
    "Type",
    "seriesType",
    listOf(
        Pair("", ""),
        Pair("Manga", "MANGA"),
        Pair("Manhua", "MANHUA"),
        Pair("Manhwa", "MANHWA"),
        Pair("Russian", "RUSSIAN"),
    ),
)

class GenreFilter : CheckBoxGroup(
    "Genres",
    "genreIds",
    listOf(
        Pair("Action", "1"),
        Pair("Adventure", "13"),
        Pair("Comedy", "7"),
        Pair("Drama", "2"),
        Pair("elf", "25"),
        Pair("Fantas", "28"),
        Pair("Fantasy", "8"),
        Pair("Historical", "19"),
        Pair("Horror", "9"),
        Pair("Josei", "21"),
        Pair("Manhwa", "5"),
        Pair("Martial Arts", "6"),
        Pair("Mature", "12"),
        Pair("Monsters", "14"),
        Pair("Reincarnation", "16"),
        Pair("Revenge", "17"),
        Pair("Romance", "20"),
        Pair("School Life", "23"),
        Pair("Seinen", "10"),
        Pair("shojo", "26"),
        Pair("Shoujo", "22"),
        Pair("Shounen", "3"),
        Pair("Slice Of Life", "18"),
        Pair("Sports", "4"),
        Pair("Supernatural", "11"),
        Pair("System", "15"),
        Pair("terror", "24"),
        Pair("Video Games", "27"),
    ),
)
