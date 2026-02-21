package eu.kanade.tachiyomi.extension.all.mangadex

import keiyoushi.lib.i18n.Intl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes

object MDConstants {

    val uuidRegex =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    const val MANGA_LIMIT = 20
    const val LATEST_CHAPTER_LIMIT = 100

    const val CHAPTER = "chapter"
    const val MANGA = "manga"
    const val COVER_ART = "cover_art"
    const val SCANLATION_GROUP = "scanlation_group"
    const val USER = "user"
    const val AUTHOR = "author"
    const val ARTIST = "artist"
    const val TAG = "tag"
    const val LIST = "custom_list"
    const val LEGACY_NO_GROUP_ID = "00e03853-1b96-4f41-9542-c71b8692033b"

    const val CDN_URL = "https://uploads.mangadex.org"
    const val API_URL = "https://api.mangadex.org"
    const val API_MANGA_URL = "$API_URL/manga"
    const val API_CHAPTER_URL = "$API_URL/chapter"
    const val API_LIST_URL = "$API_URL/list"
    val whitespaceRegex = "\\s".toRegex()

    val mdAtHomeTokenLifespan = 5.minutes.inWholeMilliseconds

    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    const val PREFIX_ID_SEARCH = "id:"
    const val PREFIX_CH_SEARCH = "ch:"
    const val PREFIX_GRP_SEARCH = "grp:"
    const val PREFIX_AUTHOR_SEARCH = "author:"
    const val PREFIX_USER_SEARCH = "usr:"
    const val PREFIX_LIST_SEARCH = "list:"

    val pathToSearchPrefix = mapOf(
        "manga" to PREFIX_ID_SEARCH,
        "title" to PREFIX_ID_SEARCH,
        "chapter" to PREFIX_CH_SEARCH,
        "group" to PREFIX_GRP_SEARCH,
        "author" to PREFIX_AUTHOR_SEARCH,
        "user" to PREFIX_USER_SEARCH,
        "list" to PREFIX_LIST_SEARCH,
    )

    private const val COVER_QUALITY_PREF = "thumbnailQuality"

    fun getCoverQualityPreferenceKey(dexLang: String): String = "${COVER_QUALITY_PREF}_$dexLang"

    fun getCoverQualityPreferenceEntries(intl: Intl) = arrayOf(intl["cover_quality_original"], intl["cover_quality_medium"], intl["cover_quality_low"])

    fun getCoverQualityPreferenceEntryValues() = arrayOf("", ".512.jpg", ".256.jpg")

    fun getCoverQualityPreferenceDefaultValue() = getCoverQualityPreferenceEntryValues()[0]

    private const val DATA_SAVER_PREF = "dataSaverV5"

    fun getDataSaverPreferenceKey(dexLang: String): String = "${DATA_SAVER_PREF}_$dexLang"

    private const val STANDARD_HTTPS_PORT_PREF = "usePort443"

    fun getStandardHttpsPreferenceKey(dexLang: String): String = "${STANDARD_HTTPS_PORT_PREF}_$dexLang"

    private const val CONTENT_RATING_PREF = "contentRating"
    const val CONTENT_RATING_PREF_VAL_SAFE = "safe"
    const val CONTENT_RATING_PREF_VAL_SUGGESTIVE = "suggestive"
    const val CONTENT_RATING_PREF_VAL_EROTICA = "erotica"
    const val CONTENT_RATING_PREF_VAL_PORNOGRAPHIC = "pornographic"
    val contentRatingPrefDefaults = setOf(CONTENT_RATING_PREF_VAL_SAFE, CONTENT_RATING_PREF_VAL_SUGGESTIVE)
    val allContentRatings = setOf(
        CONTENT_RATING_PREF_VAL_SAFE,
        CONTENT_RATING_PREF_VAL_SUGGESTIVE,
        CONTENT_RATING_PREF_VAL_EROTICA,
        CONTENT_RATING_PREF_VAL_PORNOGRAPHIC,
    )

    fun getContentRatingPrefKey(dexLang: String): String = "${CONTENT_RATING_PREF}_$dexLang"

    private const val ORIGINAL_LANGUAGE_PREF = "originalLanguage"
    const val ORIGINAL_LANGUAGE_PREF_VAL_JAPANESE = MangaDexIntl.JAPANESE
    const val ORIGINAL_LANGUAGE_PREF_VAL_CHINESE = MangaDexIntl.CHINESE
    const val ORIGINAL_LANGUAGE_PREF_VAL_CHINESE_HK = "zh-hk"
    const val ORIGINAL_LANGUAGE_PREF_VAL_KOREAN = MangaDexIntl.KOREAN
    val originalLanguagePrefDefaults = emptySet<String>()

    fun getOriginalLanguagePrefKey(dexLang: String): String = "${ORIGINAL_LANGUAGE_PREF}_$dexLang"

    private const val GROUP_AZUKI = "5fed0576-8b94-4f9a-b6a7-08eecd69800d"
    private const val GROUP_BILIBILI = "06a9fecb-b608-4f19-b93c-7caab06b7f44"
    private const val GROUP_COMIKEY = "8d8ecf83-8d42-4f8c-add8-60963f9f28d9"
    private const val GROUP_INKR = "caa63201-4a17-4b7f-95ff-ed884a2b7e60"
    private const val GROUP_MANGA_HOT = "319c1b10-cbd0-4f55-a46e-c4ee17e65139"
    private const val GROUP_MANGA_PLUS = "4f1de6a2-f0c5-4ac5-bce5-02c7dbb67deb"
    val defaultBlockedGroups = setOf(
        GROUP_AZUKI,
        GROUP_BILIBILI,
        GROUP_COMIKEY,
        GROUP_INKR,
        GROUP_MANGA_HOT,
        GROUP_MANGA_PLUS,
    )
    private const val BLOCKED_GROUPS_PREF = "blockedGroups"
    fun getBlockedGroupsPrefKey(dexLang: String): String = "${BLOCKED_GROUPS_PREF}_$dexLang"

    private const val BLOCKED_UPLOADER_PREF = "blockedUploader"
    fun getBlockedUploaderPrefKey(dexLang: String): String = "${BLOCKED_UPLOADER_PREF}_$dexLang"

    private const val HAS_SANITIZED_UUIDS_PREF = "hasSanitizedUuids"
    fun getHasSanitizedUuidsPrefKey(dexLang: String): String = "${HAS_SANITIZED_UUIDS_PREF}_$dexLang"

    private const val TRY_USING_FIRST_VOLUME_COVER_PREF = "tryUsingFirstVolumeCover"
    const val TRY_USING_FIRST_VOLUME_COVER_DEFAULT = false
    fun getTryUsingFirstVolumeCoverPrefKey(dexLang: String): String = "${TRY_USING_FIRST_VOLUME_COVER_PREF}_$dexLang"

    private const val ALT_TITLES_IN_DESC_PREF = "altTitlesInDesc"
    fun getAltTitlesInDescPrefKey(dexLang: String): String = "${ALT_TITLES_IN_DESC_PREF}_$dexLang"

    private const val PREFER_EXTENSION_LANG_TITLE_PREF = "preferExtensionLangTitle"
    fun getPreferExtensionLangTitlePrefKey(dexLang: String): String = "${PREFER_EXTENSION_LANG_TITLE_PREF}_$dexLang"

    private const val FINAL_CHAPTER_IN_DESC_PREF = "finalChapterInDesc"
    fun getFinalChapterInDescPrefKey(dexLang: String): String = "${FINAL_CHAPTER_IN_DESC_PREF}_$dexLang"

    private const val INCLUDE_UNAVAILABLE_PREF = "includeUnavailable"
    fun getIncludeUnavailablePrefKey(dexLang: String): String = "${INCLUDE_UNAVAILABLE_PREF}_$dexLang"

    private const val TAG_GROUP_CONTENT = "content"
    private const val TAG_GROUP_FORMAT = "format"
    private const val TAG_GROUP_GENRE = "genre"
    private const val TAG_GROUP_THEME = "theme"
    val TAG_GROUPS_ORDER = arrayOf(TAG_GROUP_CONTENT, TAG_GROUP_FORMAT, TAG_GROUP_GENRE, TAG_GROUP_THEME)

    const val TAG_ANTHOLOGY_UUID = "51d83883-4103-437c-b4b1-731cb73d786c"
    const val TAG_ONE_SHOT_UUID = "0234a31e-a729-4e28-9d6a-3f87c4966b9e"

    val romanizedLangCodes = mapOf(
        MangaDexIntl.JAPANESE to "ja-ro",
        MangaDexIntl.KOREAN to "ko-ro",
        MangaDexIntl.CHINESE to "zh-ro",
        "zh-hk" to "zh-ro",
    )
}
