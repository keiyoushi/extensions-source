package eu.kanade.tachiyomi.extension.en.bookwalker

const val PREF_VALIDATE_LOGGED_IN = "validateLoggedIn"
const val PREF_SHOW_LIBRARY_IN_POPULAR = "showLibraryInPopular"
const val PREF_ATTEMPT_READ_PREVIEWS = "attemptReadPreviews"
const val PREF_USE_EARLIEST_THUMBNAIL = "useEarliestThumbnail"
const val PREF_CATEGORY_EXCLUDE_REGEX = "categoryExcludeRegex"
const val PREF_GENRE_EXCLUDE_REGEX = "genreExcludeRegex"

enum class ImageQualityPref(val key: String) {
    DEVICE("device"),
    MEDIUM("medium"),
    HIGH("high"),
    ;

    companion object {
        const val PREF_KEY = "imageResolution"
        val defaultOption = DEVICE
        fun fromKey(key: String) = values().find { it.key == key } ?: defaultOption
    }
}

enum class FilterChaptersPref(val key: String) {
    OWNED("owned"),
    OBTAINABLE("obtainable"),
    ALL("all"),
    ;

    fun includes(other: FilterChaptersPref): Boolean = this >= other

    companion object {
        const val PREF_KEY = "filterChapters"
        val defaultOption = OBTAINABLE
        fun fromKey(key: String) = values().find { it.key == key } ?: defaultOption
    }
}

const val QUERY_PARAM_CATEGORY = "qcat"
const val QUERY_PARAM_GENRE = "qtag"

// const val QUERY_PARAM_AUTHOR = "qaut"
const val QUERY_PARAM_PUBLISHER = "qcom"

const val HEADER_IS_REQUEST_FROM_EXTENSION = "x-is-bookwalker-extension"
const val HEADER_PAGE_INDEX = "x-page-index"

const val USER_AGENT_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36"

const val USER_AGENT_MOBILE = "Mozilla/5.0 (Linux; Android 10; Pixel 4) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
