package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class UnionMangasFactory : SourceFactory {
    override fun createSources(): List<Source> = languages.map { UnionMangas(it) }
}

class LanguageOption(val lang: String, val infix: String = lang, val chpPrefix: String, val pageDelimiter: String)

val languages = listOf(
    LanguageOption("it", "italy", "leer", ","),
    LanguageOption("pt-BR", "manga-br", "cap", "#"),
)
