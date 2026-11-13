package eu.kanade.tachiyomi.extension.en.lgbtoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter : Filter.Select<String>("Sort", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class SubgenreFilter : Filter.Select<String>("Subgenre", SUBGENRES.map { it.first }.toTypedArray()) {
    val value get() = SUBGENRES[state].second
}

class Type(name: String, val value: String) : Filter.CheckBox(name)

class TypeFilter : Filter.Group<Type>("Type", TYPES.map { Type(it.first, it.second) }) {
    val selected get() = state.filter { it.state }.map { it.value }
}

class Tag(name: String) : Filter.TriState(name)

class TagFilter : Filter.Group<Tag>("Tags", TAGS.map(::Tag)) {
    val included get() = state.filter { it.isIncluded() }.map { it.name }
    val excluded get() = state.filter { it.isExcluded() }.map { it.name }
}

fun getFilters() = FilterList(
    SortFilter(),
    SubgenreFilter(),
    TypeFilter(),
    Filter.Header("Titles need any one of the included tags"),
    TagFilter(),
)

private val SORTS = listOf(
    "Latest Updates" to "chapter_updated_at:desc",
    "Trending (7 days)" to "views_7d:desc",
    "Recently Added" to "created_at:desc",
    "Most Viewed" to "views_total:desc",
    "Most Followed" to "follows_total:desc",
)

private val SUBGENRES = listOf(
    "All" to null,
    "Boys' Love" to "boys-love",
    "Girls' Love" to "girls-love",
)

private val TYPES = listOf(
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
    "Other" to "other",
)

// The tags titles carry: some names of the site's own menu ("Boys Love", "School") match none and filter nothing
private val TAGS = listOf(
    "4-Koma", "Action", "Adaptation", "Adult", "Adventure", "Aliens", "Animals", "Anthology", "Award Winning",
    "Boys' Love", "Comedy", "Cooking", "Crime", "Crossdressing", "Delinquents", "Demons", "Doujinshi", "Drama", "Ecchi",
    "Fantasy", "Full Color", "Genderswap", "Ghosts", "Girls' Love", "Gore", "Gyaru", "Harem", "Hentai", "Historical",
    "Horror", "Incest", "Isekai", "Loli", "Long Strip", "Mafia", "Magic", "Magical Girls", "Martial Arts", "Mature",
    "Mecha", "Medical", "Military", "Monster Girls", "Monsters", "Music", "Mystery", "Ninja", "Office Workers",
    "Official Colored", "Oneshot", "Philosophical", "Police", "Post-Apocalyptic", "Psychological", "Reincarnation",
    "Reverse Harem", "Romance", "Samurai", "School Life", "Sci-Fi", "Seinen", "Self-Published", "Sexual Violence",
    "Shota", "Shounen Ai", "Slice of Life", "Smut", "Sports", "Superhero", "Supernatural", "Survival", "Thriller",
    "Time Travel", "Traditional Games", "Tragedy", "Vampires", "Video Games", "Villainess", "Virtual Reality",
    "Web Comic", "Wuxia", "Yaoi", "Yuri", "Zombies",
)
