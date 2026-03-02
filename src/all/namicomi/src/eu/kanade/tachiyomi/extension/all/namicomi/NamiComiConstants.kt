package eu.kanade.tachiyomi.extension.all.namicomi

import keiyoushi.lib.i18n.Intl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object NamiComiConstants {
    const val MANGA_LIMIT = 20

    val whitespaceRegex = "\\s".toRegex()
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    const val LOCK_SYMBOL = "🔒"

    // Language codes used for translations
    const val ENGLISH = "en"

    // JSON discriminators
    const val CHAPTER = "chapter"
    const val MANGA = "title"
    const val COVER_ART = "cover_art"
    const val ORGANIZATION = "organization"
    const val TAG = "tag"
    const val PRIMARY_TAG = "primary_tag"
    const val SECONDARY_TAG = "secondary_tag"
    const val IMAGE_DATA = "image_data"
    const val ENTITY_ACCESS_MAP = "entity_access_map"

    // URLs & API endpoints
    const val WEB_URL = "https://namicomi.com"
    const val CDN_URL = "https://uploads.namicomi.com"
    const val API_URL = "https://api.namicomi.com"
    const val API_MANGA_URL = "$API_URL/title"
    const val API_SEARCH_URL = "$API_MANGA_URL/search"
    const val API_CHAPTER_URL = "$API_URL/chapter"
    const val API_GATING_CHECK_URL = "$API_URL/gating/check"

    // Search prefix for title ids
    const val PREFIX_ID_SEARCH = "id:"

    // Preferences
    private const val COVER_QUALITY_PREF = "thumbnailQuality"
    fun getCoverQualityPreferenceKey(extLang: String): String = "${COVER_QUALITY_PREF}_$extLang"
    fun getCoverQualityPreferenceEntries(intl: Intl) = arrayOf(intl["cover_quality_original"], intl["cover_quality_medium"], intl["cover_quality_low"])
    fun getCoverQualityPreferenceEntryValues() = arrayOf("", ".512.jpg", ".256.jpg")
    fun getCoverQualityPreferenceDefaultValue() = getCoverQualityPreferenceEntryValues()[0]

    private const val DATA_SAVER_PREF = "dataSaver"
    fun getDataSaverPreferenceKey(extLang: String): String = "${DATA_SAVER_PREF}_$extLang"

    private const val SHOW_LOCKED_CHAPTERS_PREF = "showLockedChapters"
    fun getShowLockedChaptersPreferenceKey(extLang: String): String = "${SHOW_LOCKED_CHAPTERS_PREF}_$extLang"

    // Tag types
    private const val TAG_GROUP_CONTENT = "content-warnings"
    private const val TAG_GROUP_FORMAT = "format"
    private const val TAG_GROUP_GENRE = "genre"
    private const val TAG_GROUP_THEME = "theme"
    val tagGroupsOrder = arrayOf(TAG_GROUP_CONTENT, TAG_GROUP_FORMAT, TAG_GROUP_GENRE, TAG_GROUP_THEME)
}
