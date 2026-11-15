package eu.kanade.tachiyomi.extension.en.girlstop

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    fun toUriPart() = vals[state].second
}

class Filters :
    UriPartFilter(
        "Sort By",
        arrayOf(
            "─── POPULAR ───" to "filter.php?srt=viw",
            "Popular: Week" to "filter.php?srt=viw",
            "Popular: 2 Weeks" to "filter.php?srt=viw&week2",
            "Popular: Month" to "filter.php?srt=viw&month",
            "Popular: 3 Months" to "filter.php?srt=viw&month3",
            "Popular: Half Year" to "filter.php?srt=viw&month6",

            "─── BEST ───" to "filter.php?srt=pop",
            "Best: Relevance" to "filter.php?srt=pop",
            "Best: 2 Weeks" to "filter.php?srt=pop&week2",
            "Best: Month" to "filter.php?srt=pop&month",
            "Best: 3 Months" to "filter.php?srt=pop&month3",
            "Best: Half Year" to "filter.php?srt=pop&month6",

            "─── SANDBOX ───" to "index.php?new=d",
            "Sandbox: New" to "index.php?new=d",
            "Sandbox: Random All" to "filter.php?srt=promo",
            "Sandbox: Random By New Authors" to "filter.php?srt=promonew",
            "Sandbox: Best" to "filter.php?srt=dpop",

            "─── POPULAR MODELS ───" to "models.php?popular",
            "Models: Day" to "models.php?popular",
            "Models: Week" to "models.php?popular&week",
            "Models: Month" to "models.php?popular&month",
            "Models: Favorites" to "models.php?popular&favorite",
            "Models: Sets" to "models.php?popular&sets",
        ),
    )
