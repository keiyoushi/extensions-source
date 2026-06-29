package eu.kanade.tachiyomi.extension.all.globalcomix

const val LOCK_SYMBOL = "🔒"

// Language codes used for translations
const val ENGLISH = "en"

// JSON discriminators
const val RELEASE = "Release"
const val COMIC = "Comic"
const val ARTIST = "Artist"
const val RELEASE_PAGE = "ReleasePage"

// Web requests
const val WEB_URL = "https://globalcomix.com"
const val WEB_COMIC_URL = "$WEB_URL/c"
const val WEB_CHAPTER_URL = "$WEB_URL/read"
const val API_URL = "https://api.globalcomix.com/v1"
const val API_MANGA_URL = "$API_URL/read"
const val API_CHAPTER_URL = "$API_URL/readV2"
const val API_SEARCH_URL = "$API_URL/comics"

const val CLIENT_ID = "gck_d0f170d5729446dcb3b55e6b3ebc7bf6"

// Search prefix for title ids
const val PREFIX_ID_SEARCH = "id:"

// Preferences
fun getDataSaverPreferenceKey(extLang: String): String = "dataSaver_$extLang"
fun getShowLockedChaptersPreferenceKey(extLang: String): String = "showLockedChapters_$extLang"
