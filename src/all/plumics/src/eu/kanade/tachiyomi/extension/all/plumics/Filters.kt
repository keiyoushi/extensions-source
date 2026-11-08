package eu.kanade.tachiyomi.extension.all.plumics

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class TypeFilter :
    SelectFilter(
        "Series",
        listOf(
            "Pro and Starter" to "",
            "Pro" to "pro",
            "Starter" to "starter",
        ),
    )

class SortFilter :
    SelectFilter(
        "Sort by",
        listOf(
            "Popular" to "popular",
            "Recent" to "recent",
        ),
    )

class CategoryFilter :
    SelectFilter(
        "Category",
        listOf(
            "All" to "",
            "Manga" to "manga",
            "Comics" to "comics",
            "Webtoon" to "webtoon",
            "BD" to "bd",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Genre",
        listOf("All" to "") + listOf(
            "Action", "Adventure", "Comedy", "Cyberpunk", "Demons", "Drama", "Fantasy", "Game", "Geek",
            "Historical", "Horror", "Isekai", "Kids", "Magic", "Martial Arts", "Mecha", "Music", "Mystery",
            "Parody", "Post-Apocalyptic", "Psychological", "Romance", "Sci-fi", "School", "Seinen", "Shojo",
            "Shonen", "Slice of Life", "Sport", "Supernatural", "Super Power", "Suspense", "Tragedy", "Vampire",
        ).map { it to it },
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "All" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Paused" to "paused",
        ),
    )

class FormatFilter :
    SelectFilter(
        "Format",
        listOf(
            "All" to "",
            "One-shot" to "One-shot",
            "Series" to "Serie",
        ),
    )
