package eu.kanade.tachiyomi.extension.all.cosplayrule34

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CategoryFilter :
    Filter.Select<String>(
        "Category",
        CATEGORIES.map { it.first }.toTypedArray(),
    ) {
    val selected get() = CATEGORIES[state].second

    companion object {
        // The site's menu; "thots" is its Onlyfans category
        private val CATEGORIES = listOf(
            "All" to null,
            "Cosplay" to "cosplay",
            "Asian" to "asian",
            "Onlyfans" to "thots",
            "TikTok" to "tiktok",
            "Art" to "art",
            "Celebs" to "celebs",
            "Erotic" to "erotic",
            "Tattoo" to "tattoo",
            "Dark" to "dark",
            "Fitness" to "fitness",
            "Porno" to "porno",
            "Amateur" to "amateur",
        )
    }
}

fun getFilters() = FilterList(
    Filter.Header("The category is ignored when searching"),
    CategoryFilter(),
)
