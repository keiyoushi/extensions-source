package eu.kanade.tachiyomi.extension.en.bookwalker

// const val PREF_SHOW_LIBRARY_IN_POPULAR = "showLibraryInPopular"
const val PREF_USE_LATEST_THUMBNAIL = "useLatestThumbnail"

enum class FilterChaptersPref(val key: String) {
    OWNED("owned"),
    OBTAINABLE("obtainable"),
    ALL("all"),
    ;

    fun includes(other: FilterChaptersPref): Boolean = this >= other

    companion object {
        const val PREF_KEY = "filterChapters"
        val defaultOption = OBTAINABLE
        fun fromKey(key: String) = entries.find { it.key == key } ?: defaultOption
    }
}
