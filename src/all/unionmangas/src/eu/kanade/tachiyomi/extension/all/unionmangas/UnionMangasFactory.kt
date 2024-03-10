package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class UnionMangasFactory : SourceFactory {
    override fun createSources(): List<Source> = languages.map { UnionMangas(it.lang, it.siteLang) }
}

class LanguageOption(val lang: String, val siteLang: String = lang)
private val languages = listOf(
    LanguageOption("it", "italy"),
    LanguageOption("pt-BR", "manga-br"),
)
