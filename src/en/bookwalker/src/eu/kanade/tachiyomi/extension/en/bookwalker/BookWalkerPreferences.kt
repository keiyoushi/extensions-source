package eu.kanade.tachiyomi.extension.en.bookwalker

interface BookWalkerPreferences {
    val showLibraryInPopular: Boolean
    val shouldValidateLogin: Boolean
    val imageQuality: ImageQualityPref
    val filterChapters: FilterChaptersPref
    val attemptToReadPreviews: Boolean
    val excludeCategoryFilters: Regex
    val excludeGenreFilters: Regex
}
