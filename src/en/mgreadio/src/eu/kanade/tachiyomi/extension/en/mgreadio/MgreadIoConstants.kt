package eu.kanade.tachiyomi.extension.en.mgreadio

internal const val MANGA_GRID_SELECTOR = ".manga-item-grid"
internal const val NEXT_PAGE_SELECTOR = "li:not(.uk-disabled) > a[aria-label='Next page']"
internal const val CHAPTER_REST_PAGE_SIZE = 50

internal val CHAPTER_NUMBER_REGEX = """/chapter-(\d+(?:\.\d+)?)/""".toRegex()
