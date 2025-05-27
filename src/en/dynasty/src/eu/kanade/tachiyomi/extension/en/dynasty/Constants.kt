package eu.kanade.tachiyomi.extension.en.dynasty

import java.text.SimpleDateFormat
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

const val COVER_FETCH_HOST = "keiyoushi-chapter-cover"
const val COVER_URL_FRAGMENT = "thumbnail"

val CHAPTER_SLUG_REGEX = Regex("""(.*?)_(ch[0-9_]+|volume_[0-9_\w]+)""")

val UNICODE_REGEX = Regex("\\\\u([0-9A-Fa-f]{4})")

const val AUTHORS_UPPER_LIMIT = 15

val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

const val CHAPTER_FETCH_LIMIT_PREF = "chapterFetchLimit"
val CHAPTER_FETCH_LIMITS = arrayOf("2", "5", "10", "all")
