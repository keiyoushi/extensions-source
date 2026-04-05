package eu.kanade.tachiyomi.extension.en.bookwalker

// Currently not feasible, but a future feature making it possible is anticipated
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

const val HEADER_IS_REQUEST_FROM_EXTENSION = "x-is-bookwalker-extension"
const val HEADER_PAGE_INDEX = "x-page-index"
