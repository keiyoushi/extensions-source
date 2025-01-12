package eu.kanade.tachiyomi.extension.all.namicomi

import eu.kanade.tachiyomi.lib.i18n.Intl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object NamiComiConstants {
    const val mangaLimit = 20

    val whitespaceRegex = "\\s".toRegex()
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    const val lockSymbol = "🔒"

    // Language codes used for translations
    const val english = "en"

    // JSON discriminators
    const val chapter = "chapter"
    const val manga = "title"
    const val coverArt = "cover_art"
    const val organization = "organization"
    const val tag = "tag"
    const val primaryTag = "primary_tag"
    const val secondaryTag = "secondary_tag"
    const val imageData = "image_data"
    const val entityAccessMap = "entity_access_map"

    // URLs & API endpoints
    const val webUrl = "https://namicomi.com"
    const val cdnUrl = "https://uploads.namicomi.com"
    const val apiUrl = "https://api.namicomi.com"
    const val apiMangaUrl = "$apiUrl/title"
    const val apiSearchUrl = "$apiMangaUrl/search"
    const val apiChapterUrl = "$apiUrl/chapter"
    const val apiGatingCheckUrl = "$apiUrl/gating/check"

    // Search prefix for title ids
    const val prefixIdSearch = "id:"

    // Preferences
    private const val coverQualityPref = "thumbnailQuality"
    fun getCoverQualityPreferenceKey(extLang: String): String = "${coverQualityPref}_$extLang"
    fun getCoverQualityPreferenceEntries(intl: Intl) =
        arrayOf(intl["cover_quality_original"], intl["cover_quality_medium"], intl["cover_quality_low"])
    fun getCoverQualityPreferenceEntryValues() = arrayOf("", ".512.jpg", ".256.jpg")
    fun getCoverQualityPreferenceDefaultValue() = getCoverQualityPreferenceEntryValues()[0]

    private const val dataSaverPref = "dataSaver"
    fun getDataSaverPreferenceKey(extLang: String): String = "${dataSaverPref}_$extLang"

    private const val showLockedChaptersPref = "showLockedChapters"
    fun getShowLockedChaptersPreferenceKey(extLang: String): String = "${showLockedChaptersPref}_$extLang"

    // Tag types
    private const val tagGroupContent = "content-warnings"
    private const val tagGroupFormat = "format"
    private const val tagGroupGenre = "genre"
    private const val tagGroupTheme = "theme"
    val tagGroupsOrder = arrayOf(tagGroupContent, tagGroupFormat, tagGroupGenre, tagGroupTheme)
}
