package eu.kanade.tachiyomi.extension.all.honeytoon

import eu.kanade.tachiyomi.source.SourceFactory

class HoneytoonFactory : SourceFactory {
    override fun createSources() = languageList.map(::Honeytoon)
}

class Language(
    val lang: String,
    val langPath: String = lang,
)

private val languageList = listOf(
    Language("de"),
    Language("en"),
    Language("es"),
    Language("fr"),
    Language("it"),
    Language("pt-BR", "pt"),
)
