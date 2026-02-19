package eu.kanade.tachiyomi.extension.all.yellownote

import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LanguageUtils {

    val baseLocale = Locale.ENGLISH

    private val languageToSubdomainMap = mapOf(
        "en" to "en",
        "es" to "es",
        "ko" to "kr",
        "zh-Hans" to null,
        "zh-Hant" to "tw",
    )

    private val languageToDisplayNameMap = mapOf(
        "en" to "English",
        "es" to "Español",
        "ko" to "한국어",
        "zh-Hans" to "简体中文",
        "zh-Hant" to "繁體中文",
    )

    val supportedLocaleTags = languageToSubdomainMap.keys.toTypedArray()

    fun getDefaultLanguage(): String {
        val defaultLocale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList.getDefault().getFirstMatch(supportedLocaleTags) ?: baseLocale
            } else {
                Locale.getDefault()
            }

        return when {
            defaultLocale.script == "Hant" -> "zh-Hant"
            defaultLocale.script == "Hans" -> "zh-Hans"
            defaultLocale.country in listOf("TW", "HK", "MO") -> "zh-Hant"
            defaultLocale.country in listOf("CN", "SG") -> "zh-Hans"
            else -> baseLocale.language
        }
    }

    fun getSubdomainByLanguage(lang: String): String? = languageToSubdomainMap[lang]

    fun getSupportedLanguageKeys(): Array<String> = languageToDisplayNameMap.keys.toTypedArray()

    fun getSupportedLanguageDisplayNames(): Array<String> = languageToDisplayNameMap.values.toTypedArray()
}
