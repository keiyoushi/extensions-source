package eu.kanade.tachiyomi.multisrc.machinetranslations

class MachineTranslationsFactoryUtils

data class Language(
    val lang: String,
    val target: String = lang,
    val origin: String = "en",
    val fontSize: Int = 24,
    val disableFontSettings: Boolean = false,
    val disableWordBreak: Boolean = false,
    val disableTranslator: Boolean = false,
    val supportNativeTranslation: Boolean = false,
    val fontName: String = "",
)
