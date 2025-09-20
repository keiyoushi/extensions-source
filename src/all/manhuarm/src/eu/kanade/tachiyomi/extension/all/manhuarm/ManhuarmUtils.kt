package eu.kanade.tachiyomi.extension.all.manhuarm

class MachineTranslationsFactoryUtils

data class Language(
    val lang: String,
    val target: String = lang,
    val origin: String = "en",
    val fontSize: Int = 28,
    val dialogBoxScale: Float = 1f,
    val disableFontSettings: Boolean = false,
    val disableWordBreak: Boolean = false,
    val disableTranslator: Boolean = false,
    val supportNativeTranslation: Boolean = false,
    val fontName: String = "comic_neue_bold",
)
