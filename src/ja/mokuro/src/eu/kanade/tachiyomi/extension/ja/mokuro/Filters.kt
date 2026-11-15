package eu.kanade.tachiyomi.extension.ja.mokuro

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Sort(
        "Sort",
        arrayOf("Title", "Latest update", "Catalog order"),
        Selection(0, true),
    )

class TagFilter : Filter.Select<String>("Tag", TAGS) {
    companion object {
        // All edition/quality tags observed in catalog.json as of 2026-08-29
        val TAGS = arrayOf(
            "All",
            "Colored",
            "Colored, Upscaled",
            "Full Colour version, upscaled",
            "HD Scan",
            "ReMaster Edition",
            "Semi-Colored",
            "Upscale",
            "Upscaled",
            "Webcomic",
        )
    }
}
