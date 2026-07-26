package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter : Filter.Select<String>("Status", arrayOf("Serializing", "Completed", "One-shot")) {
    val type: String get() = TYPES[state]
    val isDefault: Boolean get() = state == 0

    companion object {
        private val TYPES = arrayOf("serializing", "completed", "one-shot")
        const val DEFAULT_TYPE = "serializing"
    }
}

class GenreFilter(genres: List<TagName>) : Filter.Select<String>("Genre", (listOf("All") + genres.map(TagName::name)).toTypedArray()) {
    private val slugs = listOf("") + genres.map(TagName::slug)
    val slug: String get() = slugs[state]
}
