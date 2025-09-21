package eu.kanade.tachiyomi.extension.all.comicklive

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ComickFactory : SourceFactory {
    override fun createSources(): List<Source> = languages.map {
        object : Comick(it.lang, it.comickLang) {}
    }
}

class ComickLanguageOption(val lang: String, val comickLang: String = lang)

private val languages = listOf(
    ComickLanguageOption("all", ""),
    ComickLanguageOption("en"),
    ComickLanguageOption("pt-BR", "pt-br"),
    ComickLanguageOption("ru"),
    ComickLanguageOption("fr"),
    ComickLanguageOption("es-419"),
    ComickLanguageOption("pl"),
    ComickLanguageOption("tr"),
    ComickLanguageOption("it"),
    ComickLanguageOption("es"),
    ComickLanguageOption("id"),
    ComickLanguageOption("hu"),
    ComickLanguageOption("vi"),
    ComickLanguageOption("zh-Hant", "zh-hk"),
    ComickLanguageOption("ar"),
    ComickLanguageOption("de"),
    ComickLanguageOption("zh-Hans", "zh"),
    ComickLanguageOption("ca"),
    ComickLanguageOption("bg"),
    ComickLanguageOption("th"),
    ComickLanguageOption("fa"),
    ComickLanguageOption("uk"),
    ComickLanguageOption("mn"),
    ComickLanguageOption("ro"),
    ComickLanguageOption("he"),
    ComickLanguageOption("ms"),
    ComickLanguageOption("tl"),
    ComickLanguageOption("ja"),
    ComickLanguageOption("hi"),
    ComickLanguageOption("my"),
    ComickLanguageOption("ko"),
    ComickLanguageOption("cs"),
    ComickLanguageOption("pt"),
    ComickLanguageOption("nl"),
    ComickLanguageOption("sv"),
    ComickLanguageOption("bn"),
    ComickLanguageOption("no"),
    ComickLanguageOption("lt"),
    ComickLanguageOption("el"),
    ComickLanguageOption("sr"),
    ComickLanguageOption("da"),
)
