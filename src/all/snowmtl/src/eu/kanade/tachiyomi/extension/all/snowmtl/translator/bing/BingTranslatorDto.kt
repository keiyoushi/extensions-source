package eu.kanade.tachiyomi.extension.all.snowmtl.translator.bing
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import kotlinx.serialization.Serializable

class BingTranslatorDto

class TokenGroup(
    val token: String = "",
    val key: String = "",
    val iid: String = "",
    val ig: String = "",
) {
    fun isNotValid() = listOf(token, key, iid, ig).any(String::isBlank)

    fun isValid() = isNotValid().not()
}

@Serializable
class TranslateDto(
    val translations: List<TextTranslated>,
) {
    val text = translations.firstOrNull()?.text ?: ""
}

@Serializable
class TextTranslated(
    val text: String,
)
