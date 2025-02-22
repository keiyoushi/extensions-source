package eu.kanade.tachiyomi.extension.en.mangaplanet

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

class SortFilter : SelectFilter(
    "Sort order",
    "sort",
    arrayOf(
        Pair("Released last", ""),
        Pair("Released first", "1"),
        Pair("By A to Z", "2"),
    ),
)

class CategoryFilter : MultiSelectFilter(
    "Category",
    "cat",
    listOf(
        MultiSelectOption("Shojo/Josei", "3"),
        MultiSelectOption("Shonen/Seinen", "1"),
        MultiSelectOption("BL(futekiya)", "2"),
        MultiSelectOption("GL/Yuri", "4"),
    ),
)

class SpicyLevelFilter : MultiSelectFilter(
    "Spicy Level - BL(futekiya) only",
    "hp",
    listOf(
        MultiSelectOption("🌶️🌶️🌶️🌶️🌶️", "5"),
        MultiSelectOption("🌶️🌶️🌶️🌶️️", "4"),
        MultiSelectOption("🌶️🌶️🌶️", "3"),
        MultiSelectOption("🌶️🌶️", "2"),
        MultiSelectOption("🌶️", "1"),
    ),
)

class AccessTypeFilter : SelectFilter(
    "Access Type",
    "bt",
    arrayOf(
        Pair("All", ""),
        Pair("Access for free", "1"),
        Pair("Access via Points", "2"),
        Pair("Access via Manga Planet Pass", "3"),
    ),
)

class FormatFilter : MultiSelectFilter(
    "Format",
    "fmt",
    listOf(
        MultiSelectOption("Manga", "1"),
        MultiSelectOption("TatéManga", "2"),
        MultiSelectOption("Novel", "3"), // Novels are images with text
    ),
)

class RatingFilter : MultiSelectFilter(
    "Rating",
    "rtg",
    listOf(
        MultiSelectOption("All Ages", "0"),
        MultiSelectOption("R16+", "16"),
        MultiSelectOption("R18+", "18"),
    ),
)

class ReleaseStatusFilter : SelectFilter(
    "Release status",
    "comp",
    arrayOf(
        Pair("All", ""),
        Pair("Ongoing", "progress"),
        Pair("Completed", "comp"),
    ),
)

class LetterFilter : SelectFilter(
    "Display by First Letter",
    "fl",
    buildList {
        add(Pair("All", ""))

        for (letter in 'A'..'Z') {
            add(Pair(letter.toString(), letter.toString()))
        }

        add(Pair("Other", "other"))
    }
        .toTypedArray(),
)

open class MultiSelectFilter(
    name: String,
    val queryParameter: String,
    options: List<MultiSelectOption>,
) : Filter.Group<MultiSelectOption>(name, options), UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val enabled = state.filter { it.state }

        if (enabled.isEmpty() || enabled.size == state.size) {
            return
        }

        builder.addQueryParameter(
            queryParameter,
            enabled.joinToString(",") { it.value },
        )
    }
}

class MultiSelectOption(name: String, val value: String, state: Boolean = false) : Filter.CheckBox(name, state)

open class SelectFilter(
    name: String,
    val queryParameter: String,
    val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state), UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state == 0) {
            return
        }

        builder.addQueryParameter(queryParameter, vals[state].second)
    }
}
