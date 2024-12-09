package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.lib.i18n.Intl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes

object MDConstants {

    val uuidRegex =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    const val mangaLimit = 20
    const val latestChapterLimit = 100

    const val chapter = "chapter"
    const val manga = "manga"
    const val coverArt = "cover_art"
    const val scanlationGroup = "scanlation_group"
    const val user = "user"
    const val author = "author"
    const val artist = "artist"
    const val tag = "tag"
    const val list = "custom_list"
    const val legacyNoGroupId = "00e03853-1b96-4f41-9542-c71b8692033b"

    const val cdnUrl = "https://uploads.mangadex.org"
    const val apiUrl = "https://api.mangadex.org"
    const val apiMangaUrl = "$apiUrl/manga"
    const val apiChapterUrl = "$apiUrl/chapter"
    const val apiListUrl = "$apiUrl/list"
    const val atHomePostUrl = "https://api.mangadex.network/report"
    val whitespaceRegex = "\\s".toRegex()

    val mdAtHomeTokenLifespan = 5.minutes.inWholeMilliseconds

    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    const val prefixIdSearch = "id:"
    const val prefixChSearch = "ch:"
    const val prefixGrpSearch = "grp:"
    const val prefixAuthSearch = "author:"
    const val prefixUsrSearch = "usr:"
    const val prefixListSearch = "list:"

    private const val coverQualityPref = "thumbnailQuality"

    fun getCoverQualityPreferenceKey(dexLang: String): String {
        return "${coverQualityPref}_$dexLang"
    }

    fun getCoverQualityPreferenceEntries(intl: Intl) =
        arrayOf(intl["cover_quality_original"], intl["cover_quality_medium"], intl["cover_quality_low"])

    fun getCoverQualityPreferenceEntryValues() = arrayOf("", ".512.jpg", ".256.jpg")

    fun getCoverQualityPreferenceDefaultValue() = getCoverQualityPreferenceEntryValues()[0]

    private const val dataSaverPref = "dataSaverV5"

    fun getDataSaverPreferenceKey(dexLang: String): String {
        return "${dataSaverPref}_$dexLang"
    }

    private const val standardHttpsPortPref = "usePort443"

    fun getStandardHttpsPreferenceKey(dexLang: String): String {
        return "${standardHttpsPortPref}_$dexLang"
    }

    private const val contentRatingPref = "contentRating"
    const val contentRatingPrefValSafe = "safe"
    const val contentRatingPrefValSuggestive = "suggestive"
    const val contentRatingPrefValErotica = "erotica"
    const val contentRatingPrefValPornographic = "pornographic"
    val contentRatingPrefDefaults = setOf(contentRatingPrefValSafe, contentRatingPrefValSuggestive)
    val allContentRatings = setOf(
        contentRatingPrefValSafe,
        contentRatingPrefValSuggestive,
        contentRatingPrefValErotica,
        contentRatingPrefValPornographic,
    )

    fun getContentRatingPrefKey(dexLang: String): String {
        return "${contentRatingPref}_$dexLang"
    }

    private const val originalLanguagePref = "originalLanguage"
    const val originalLanguagePrefValJapanese = MangaDexIntl.JAPANESE
    const val originalLanguagePrefValChinese = MangaDexIntl.CHINESE
    const val originalLanguagePrefValChineseHk = "zh-hk"
    const val originalLanguagePrefValKorean = MangaDexIntl.KOREAN
    val originalLanguagePrefDefaults = emptySet<String>()

    fun getOriginalLanguagePrefKey(dexLang: String): String {
        return "${originalLanguagePref}_$dexLang"
    }

    private const val groupAzuki = "5fed0576-8b94-4f9a-b6a7-08eecd69800d"
    private const val groupBilibili = "06a9fecb-b608-4f19-b93c-7caab06b7f44"
    private const val groupComikey = "8d8ecf83-8d42-4f8c-add8-60963f9f28d9"
    private const val groupInkr = "caa63201-4a17-4b7f-95ff-ed884a2b7e60"
    private const val groupMangaHot = "319c1b10-cbd0-4f55-a46e-c4ee17e65139"
    private const val groupMangaPlus = "4f1de6a2-f0c5-4ac5-bce5-02c7dbb67deb"
    val defaultBlockedGroups = setOf(
        groupAzuki,
        groupBilibili,
        groupComikey,
        groupInkr,
        groupMangaHot,
        groupMangaPlus,
    )
    private const val blockedGroupsPref = "blockedGroups"
    fun getBlockedGroupsPrefKey(dexLang: String): String {
        return "${blockedGroupsPref}_$dexLang"
    }

    private const val blockedUploaderPref = "blockedUploader"
    fun getBlockedUploaderPrefKey(dexLang: String): String {
        return "${blockedUploaderPref}_$dexLang"
    }

    private const val hasSanitizedUuidsPref = "hasSanitizedUuids"
    fun getHasSanitizedUuidsPrefKey(dexLang: String): String {
        return "${hasSanitizedUuidsPref}_$dexLang"
    }

    private const val tryUsingFirstVolumeCoverPref = "tryUsingFirstVolumeCover"
    const val tryUsingFirstVolumeCoverDefault = false
    fun getTryUsingFirstVolumeCoverPrefKey(dexLang: String): String {
        return "${tryUsingFirstVolumeCoverPref}_$dexLang"
    }

    private const val altTitlesInDescPref = "altTitlesInDesc"
    fun getAltTitlesInDescPrefKey(dexLang: String): String {
        return "${altTitlesInDescPref}_$dexLang"
    }

    private const val preferExtensionLangTitlePref = "preferExtensionLangTitle"
    fun getPreferExtensionLangTitlePrefKey(dexLang: String): String {
        return "${preferExtensionLangTitlePref}_$dexLang"
    }

    private const val tagGroupContent = "content"
    private const val tagGroupFormat = "format"
    private const val tagGroupGenre = "genre"
    private const val tagGroupTheme = "theme"
    val tagGroupsOrder = arrayOf(tagGroupContent, tagGroupFormat, tagGroupGenre, tagGroupTheme)

    const val tagAnthologyUuid = "51d83883-4103-437c-b4b1-731cb73d786c"
    const val tagOneShotUuid = "0234a31e-a729-4e28-9d6a-3f87c4966b9e"

    val romanizedLangCodes = mapOf(
        MangaDexIntl.JAPANESE to "ja-ro",
        MangaDexIntl.KOREAN to "ko-ro",
        MangaDexIntl.CHINESE to "zh-ro",
        "zh-hk" to "zh-ro",
    )
}
