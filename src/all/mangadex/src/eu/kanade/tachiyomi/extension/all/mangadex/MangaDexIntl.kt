package eu.kanade.tachiyomi.extension.all.mangadex
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

object MangaDexIntl {
    const val BRAZILIAN_PORTUGUESE = "pt-BR"
    const val CHINESE = "zh"
    const val ENGLISH = "en"
    const val JAPANESE = "ja"
    const val KOREAN = "ko"
    const val PORTUGUESE = "pt"
    const val SPANISH_LATAM = "es-419"
    const val SPANISH = "es"
    const val RUSSIAN = "ru"

    val AVAILABLE_LANGS = setOf(
        ENGLISH,
        BRAZILIAN_PORTUGUESE,
        PORTUGUESE,
        SPANISH,
        SPANISH_LATAM,
        RUSSIAN,
    )

    const val MANGADEX_NAME = "MangaDex"
}
