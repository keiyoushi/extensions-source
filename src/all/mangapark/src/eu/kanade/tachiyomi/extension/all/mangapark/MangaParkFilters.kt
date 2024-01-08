package eu.kanade.tachiyomi.extension.all.mangapark

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
) {
    val checked get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
}

class TriStateFilter(name: String, val value: String) : Filter.TriState(name)

abstract class TriStateGroup(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it.first, it.second) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.value }.takeUnless { it.isEmpty() }
    val excluded get() = state.filter { it.isExcluded() }.map { it.value }.takeUnless { it.isEmpty() }
}

class SortFilter(defaultOrder: String? = null) : SelectFilter("Sort By", sort, defaultOrder) {
    companion object {
        private val sort = listOf(
            Pair("Rating Score", "field_score"),
            Pair("Most Follows", "field_follow"),
            Pair("Most Reviews", "field_review"),
            Pair("Most Comments", "field_comment"),
            Pair("Most Chapters", "field_chapter"),
            Pair("New Chapters", "field_update"),
            Pair("Recently Created", "field_create"),
            Pair("Name A-Z", "field_name"),
            Pair("Total Views", "views_d000"),
            Pair("Most Views 360 days", "views_d360"),
            Pair("Most Views 180 days", "views_d180"),
            Pair("Most Views 90 days", "views_d090"),
            Pair("Most Views 30 days", "views_d030"),
            Pair("Most Views 7 days", "views_d007"),
            Pair("Most Views 24 hours", "views_h024"),
            Pair("Most Views 12 hours", "views_h012"),
            Pair("Most Views 6 hours", "views_h006"),
            Pair("Most Views 60 minutes", "views_h001"),
        )

        val POPULAR = FilterList(SortFilter("field_score"))
        val LATEST = FilterList(SortFilter("field_update"))
    }
}

class GenreFilter(genres: List<Pair<String, String>>) : TriStateGroup("Genres", genres)

abstract class StatusFilter(name: String) : SelectFilter(name, status) {
    companion object {
        private val status = listOf(
            Pair("All", ""),
            Pair("Pending", "pending"),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled"),
        )
    }
}

class OriginalLanguageFilter : CheckBoxGroup("Original Work Language", language) {
    companion object {
        private val language = listOf(
            Pair("Chinese", "zh"),
            Pair("English", "en"),
            Pair("Japanese", "ja"),
            Pair("Korean", "ko"),
        )
    }
}

class OriginalStatusFilter : StatusFilter("Original Work Status")

class UploadStatusFilter : StatusFilter("Upload Status")

class ChapterCountFilter : SelectFilter("Number of Chapters", chapters) {
    companion object {
        private val chapters = listOf(
            Pair("", ""),
            Pair("0", "0"),
            Pair("1+", "1"),
            Pair("10+", "10"),
            Pair("20+", "20"),
            Pair("30+", "30"),
            Pair("40+", "40"),
            Pair("50+", "50"),
            Pair("60+", "60"),
            Pair("70+", "70"),
            Pair("80+", "80"),
            Pair("90+", "90"),
            Pair("100+", "100"),
            Pair("200+", "200"),
            Pair("300+", "300"),
            Pair("299~200", "200-299"),
            Pair("199~100", "100-199"),
            Pair("99~90", "90-99"),
            Pair("89~80", "80-89"),
            Pair("79~70", "70-79"),
            Pair("69~60", "60-69"),
            Pair("59~50", "50-59"),
            Pair("49~40", "40-49"),
            Pair("39~30", "30-39"),
            Pair("29~20", "20-29"),
            Pair("19~10", "10-19"),
            Pair("9~1", "1-9"),
        )
    }
}
