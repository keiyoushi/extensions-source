package eu.kanade.tachiyomi.extension.en.dynasty

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val SERIES_TYPE = "Series"
const val CHAPTER_TYPE = "Chapter"
const val ANTHOLOGY_TYPE = "Anthology"
const val DOUJIN_TYPE = "Doujin"
const val ISSUE_TYPE = "Issue"

const val SERIES_DIR = "series"
const val CHAPTERS_DIR = "chapters"
const val ANTHOLOGIES_DIR = "anthologies"
const val DOUJINS_DIR = "doujins"
const val ISSUES_DIR = "issues"

val MANGA_TYPES = setOf(SERIES_TYPE, ANTHOLOGY_TYPE, DOUJIN_TYPE, ISSUE_TYPE)

val MANGA_DIRS = listOf(SERIES_DIR, ANTHOLOGIES_DIR, DOUJINS_DIR, ISSUES_DIR, CHAPTERS_DIR)

const val COVER_FETCH_HOST = "keiyoushi-chapter-cover"
const val COVER_URL_FRAGMENT = "thumbnail"

val COVER_EXTENSIONS = listOf(
    "jpg", "jpeg", "png", "webp", "jfif", "gif",
    "JPG", "JPEG", "PNG", "WEBP", "JFIF", "GIF",
)

val CHAPTER_SLUG_REGEX = Regex("""(.*?)_(ch[0-9_]+|volume_[0-9_\w]+)""")

val UNICODE_REGEX = Regex("\\\\u([0-9A-Fa-f]{4})")

const val AUTHORS_UPPER_LIMIT = 15

val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    .withZone(ZoneId.of("UTC"))

const val CHAPTER_FETCH_LIMIT_PREF = "chapterFetchLimit"
val CHAPTER_FETCH_LIMITS = arrayOf("2", "5", "10", "all")

// sort filters
const val SMART_SORT = "_smart_"
const val BEST_MATCH = ""
const val RELEASED_ON = "released_on"
