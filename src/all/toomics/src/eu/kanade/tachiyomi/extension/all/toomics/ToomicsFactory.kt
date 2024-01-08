package eu.kanade.tachiyomi.extension.all.toomics

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ToomicsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ToomicsEnglish(),
        ToomicsSimplifiedChinese(),
        ToomicsTraditionalChinese(),
        ToomicsSpanishLA(),
        ToomicsSpanish(),
        ToomicsItalian(),
        ToomicsGerman(),
        ToomicsFrench(),
        ToomicsPortuguese(),
    )
}

class ToomicsEnglish : ToomicsGlobal("en", SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH))
class ToomicsSimplifiedChinese : ToomicsGlobal("sc", SimpleDateFormat("yyyy.MM.dd", Locale.SIMPLIFIED_CHINESE), "zh-Hans") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 2191753978421234924
}
class ToomicsTraditionalChinese : ToomicsGlobal("tc", SimpleDateFormat("yyyy.MM.dd", Locale.TRADITIONAL_CHINESE), "zh-Hant") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 371640888113435809
}
class ToomicsSpanishLA : ToomicsGlobal("mx", SimpleDateFormat("d MMM, yyyy", Locale("es", "419")), "es-419") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 7362369816539610504
}
class ToomicsSpanish : ToomicsGlobal("es", SimpleDateFormat("d MMM, yyyy", Locale("es", "419")), "es")
class ToomicsItalian : ToomicsGlobal("it", SimpleDateFormat("d MMM, yyyy", Locale.ITALIAN))
class ToomicsGerman : ToomicsGlobal("de", SimpleDateFormat("d. MMM yyyy", Locale.GERMAN))
class ToomicsFrench : ToomicsGlobal("fr", SimpleDateFormat("dd MMM. yyyy", Locale.ENGLISH))

class ToomicsPortuguese : ToomicsGlobal("por", SimpleDateFormat("d 'de' MMM 'de' yyyy", Locale("pt", "BR")), "pt-BR") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 4488498756724948818
}
