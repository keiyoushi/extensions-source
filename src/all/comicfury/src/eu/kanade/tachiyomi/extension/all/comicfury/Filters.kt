package eu.kanade.tachiyomi.extension.all.comicfury

import eu.kanade.tachiyomi.source.model.Filter

internal class SortFilter(index: Int) :
    Filter.Select<String>(
        "Sort By",
        arrayOf("Relevance", "Popularity", "Last Update"),
        index,
    )

internal class CompletedComicFilter : Filter.CheckBox("Comic Completed", false)

internal class LastUpdatedFilter :
    Filter.Select<String>(
        "Last Updated",
        arrayOf("All Time", "This Week", "This Month", "This Year", "Completed Only"),
        0,
    )

internal class ViolenceFilter :
    Filter.Select<String>(
        "Violence",
        arrayOf("None / Minimal", "Violent Content", "Gore / Graphic"),
        2,
    )

internal class NudityFilter :
    Filter.Select<String>(
        "Frontal Nudity",
        arrayOf("None", "Occasional", "Frequent"),
        2,
    )

internal class StrongLangFilter :
    Filter.Select<String>(
        "Strong Language",
        arrayOf("None", "Occasional", "Frequent"),
        2,
    )

internal class SexualFilter :
    Filter.Select<String>(
        "Sexual Content",
        arrayOf("No Sexual Content", "Sexual Situations", "Strong Sexual Themes"),
        2,
    )

internal class TagsFilter : Filter.Text("Tags")
