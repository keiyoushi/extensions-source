package eu.kanade.tachiyomi.extension.all.manhuarm.translator.bing

import kotlinx.serialization.Serializable

class TokenGroup(
    val token: String = "",
    val key: String = "",
    val iid: String = "",
    val ig: String = "",
) {
    fun isValid() = listOf(token, key, iid, ig).none(String::isBlank)
}

@Serializable
class TranslateDto(
    private val translations: List<TextTranslated>,
) {
    val text get() = translations.first().text
}

@Serializable
class TextTranslated(
    val text: String,
)
