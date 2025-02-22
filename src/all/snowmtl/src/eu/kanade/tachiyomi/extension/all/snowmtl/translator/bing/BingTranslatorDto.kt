package eu.kanade.tachiyomi.extension.all.snowmtl.translator.bing

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
