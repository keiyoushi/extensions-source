@file:Suppress("ktlint:standard:filename")

package eu.kanade.tachiyomi.extension.all.toonlook

import eu.kanade.tachiyomi.source.model.Filter

internal class GenreFilter(genres: List<GenreDto>) : Filter.Select<String>("Genre", (listOf("All") + genres.map { it.name }).toTypedArray()) {
    private val slugs = listOf<String?>(null) + genres.map { it.slug }

    fun toSlug(): String? = slugs[state]
}
