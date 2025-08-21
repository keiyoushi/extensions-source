package eu.kanade.tachiyomi.multisrc.machinetranslations

class MachineTranslationsFactoryUtils

class Language(
    val lang: String,
    val target: String = lang,
    val origin: String = "en",
    var fontSize: Int = 24,
    var disableSourceSettings: Boolean = false,
    var disableWordBreak: Boolean = false,
    var disableTranslator: Boolean = false,
    var supportNativeTranslation: Boolean = false,
)
