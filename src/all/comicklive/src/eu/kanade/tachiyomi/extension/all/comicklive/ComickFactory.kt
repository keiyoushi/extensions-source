package eu.kanade.tachiyomi.extension.all.comicklive

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ComickFactory : SourceFactory {
    override fun createSources(): List<Source> = languages.map {
        object : Comick(it.lang, it.comickLang) {}
    }
}

class LanguageOption(val lang: String, val comickLang: String = lang)

private val languages = listOf(
    LanguageOption("all", ""),
    LanguageOption("en"),
    LanguageOption("pt-BR", "pt-br"),
    LanguageOption("ru"),
    LanguageOption("fr"),
    LanguageOption("es-419"),
    LanguageOption("pl"),
    LanguageOption("tr"),
    LanguageOption("it"),
    LanguageOption("es"),
    LanguageOption("id"),
    LanguageOption("hu"),
    LanguageOption("vi"),
    LanguageOption("zh-Hant", "zh-hk"),
    LanguageOption("ar"),
    LanguageOption("de"),
    LanguageOption("zh-Hans", "zh"),
    LanguageOption("ca"),
    LanguageOption("bg"),
    LanguageOption("th"),
    LanguageOption("fa"),
    LanguageOption("uk"),
    LanguageOption("mn"),
    LanguageOption("ro"),
    LanguageOption("he"),
    LanguageOption("ms"),
    LanguageOption("tl"),
    LanguageOption("ja"),
    LanguageOption("hi"),
    LanguageOption("my"),
    LanguageOption("ko"),
    LanguageOption("cs"),
    LanguageOption("pt"),
    LanguageOption("nl"),
    LanguageOption("sv"),
    LanguageOption("bn"),
    LanguageOption("no"),
    LanguageOption("lt"),
    LanguageOption("el"),
    LanguageOption("sr"),
    LanguageOption("da"),
)
