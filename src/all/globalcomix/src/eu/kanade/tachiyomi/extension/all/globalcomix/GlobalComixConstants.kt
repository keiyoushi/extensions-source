package eu.kanade.tachiyomi.extension.all.globalcomix

const val lockSymbol = "🔒"

// Language codes used for translations
const val english = "en"

// JSON discriminators
const val release = "Release"
const val comic = "Comic"
const val artist = "Artist"
const val releasePage = "ReleasePage"

// Web requests
const val webUrl = "https://globalcomix.com"
const val webComicUrl = "$webUrl/c"
const val webChapterUrl = "$webUrl/read"
const val apiUrl = "https://api.globalcomix.com/v1"
const val apiMangaUrl = "$apiUrl/read"
const val apiChapterUrl = "$apiUrl/readV2"
const val apiSearchUrl = "$apiUrl/comics"

const val clientId = "gck_d0f170d5729446dcb3b55e6b3ebc7bf6"

// Search prefix for title ids
const val prefixIdSearch = "id:"

// Preferences
fun getDataSaverPreferenceKey(extLang: String): String = "dataSaver_$extLang"
fun getShowLockedChaptersPreferenceKey(extLang: String): String = "showLockedChapters_$extLang"
