package eu.kanade.tachiyomi.extension.en.bbato

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.util.Calendar

fun getFilters(): FilterList = FilterList(
    TypeFilter(),
    GenreFilter(),
    StatusFilter(),
    YearFilter(),
    MinChapterFilter(),
    SortFilter(),
)

class CheckBoxVal(name: String, val value: String) : Filter.CheckBox(name)

class TypeFilter : Filter.Group<CheckBoxVal>("Type", typeList)
class GenreFilter : Filter.Group<CheckBoxVal>("Genre", genreList)
class StatusFilter : Filter.Group<CheckBoxVal>("Status", statusList)
class YearFilter : Filter.Group<CheckBoxVal>("Year", yearList)

class MinChapterFilter : Filter.Select<String>("Length", minChapNames) {
    val selectedValue get() = minChapValues[state]
}

class SortFilter : Filter.Select<String>("Sort by", sortNames) {
    val selectedValue get() = sortValues[state]
}

private val typeList = listOf(
    CheckBoxVal("Manga", "manga"),
    CheckBoxVal("One Shot", "one-shot"),
    CheckBoxVal("Doujinshi", "doujinshi"),
    CheckBoxVal("Novel", "novel"),
    CheckBoxVal("Manhwa", "manhwa"),
    CheckBoxVal("Manhua", "manhua"),
)

private val genreList = listOf(
    CheckBoxVal("Action", "action"),
    CheckBoxVal("Adventure", "adventure"),
    CheckBoxVal("Avant Garde", "avant-garde"),
    CheckBoxVal("Boys Love", "boys-love"),
    CheckBoxVal("Comedy", "comedy"),
    CheckBoxVal("Demons", "demons"),
    CheckBoxVal("Drama", "drama"),
    CheckBoxVal("Ecchi", "ecchi"),
    CheckBoxVal("Fantasy", "fantasy"),
    CheckBoxVal("Girls Love", "girls-love"),
    CheckBoxVal("Gourmet", "gourmet"),
    CheckBoxVal("Harem", "harem"),
    CheckBoxVal("Horror", "horror"),
    CheckBoxVal("Isekai", "isekai"),
    CheckBoxVal("Iyashikei", "iyashikei"),
    CheckBoxVal("Josei", "josei"),
    CheckBoxVal("Kids", "kids"),
    CheckBoxVal("Magic", "magic"),
    CheckBoxVal("Mahou Shoujo", "mahou-shoujo"),
    CheckBoxVal("Martial Arts", "martial-arts"),
    CheckBoxVal("Mecha", "mecha"),
    CheckBoxVal("Military", "military"),
    CheckBoxVal("Music", "music"),
    CheckBoxVal("Mystery", "mystery"),
    CheckBoxVal("Parody", "parody"),
    CheckBoxVal("Psychological", "psychological"),
    CheckBoxVal("Reverse Harem", "reverse-harem"),
    CheckBoxVal("Romance", "romance"),
    CheckBoxVal("School", "school"),
    CheckBoxVal("Sci-Fi", "sci-fi"),
    CheckBoxVal("Seinen", "seinen"),
    CheckBoxVal("Shoujo", "shoujo"),
    CheckBoxVal("Shounen", "shounen"),
    CheckBoxVal("Slice of Life", "slice-of-life"),
    CheckBoxVal("Space", "space"),
    CheckBoxVal("Sports", "sports"),
    CheckBoxVal("Super Power", "super-power"),
    CheckBoxVal("Supernatural", "supernatural"),
    CheckBoxVal("Suspense", "suspense"),
    CheckBoxVal("Thriller", "thriller"),
    CheckBoxVal("Vampire", "vampire"),
)

private val statusList = listOf(
    CheckBoxVal("Completed", "completed"),
    CheckBoxVal("Releasing", "releasing"),
    CheckBoxVal("On Hiatus", "on_hiatus"),
    CheckBoxVal("Discontinued", "discontinued"),
    CheckBoxVal("Not Yet Published", "info"),
)

private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

private val yearList = (currentYear downTo 2005).map {
    CheckBoxVal(it.toString(), it.toString())
} + listOf(
    CheckBoxVal("2000s", "2000s"),
    CheckBoxVal("1990s", "1990s"),
    CheckBoxVal("1980s", "1980s"),
    CheckBoxVal("1970s", "1970s"),
    CheckBoxVal("1960s", "1960s"),
    CheckBoxVal("1950s", "1950s"),
    CheckBoxVal("1940s", "1940s"),
    CheckBoxVal("1930s", "1930s"),
)

private val minChapNames = arrayOf("Any", ">= 1 chapters", ">= 3 chapters", ">= 5 chapters", ">= 10 chapters", ">= 20 chapters", ">= 30 chapters", ">= 50 chapters")
private val minChapValues = arrayOf("", "1", "3", "5", "10", "20", "30", "50")

private val sortNames = arrayOf("Recently updated", "Recently added", "Release date", "Name A-Z")
private val sortValues = arrayOf("recently_updated", "recently_added", "release_date", "title_az")
