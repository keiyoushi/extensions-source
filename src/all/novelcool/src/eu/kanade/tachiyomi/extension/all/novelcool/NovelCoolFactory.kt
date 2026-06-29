package eu.kanade.tachiyomi.extension.all.novelcool

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NovelCoolFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NovelCool("https://www.novelcool.com", "en"),
        NovelCool("https://es.novelcool.com", "es"),
        NovelCool("https://de.novelcool.com", "de"),
        NovelCool("https://ru.novelcool.com", "ru"),
        NovelCool("https://it.novelcool.com", "it"),
        NovelCool("https://br.novelcool.com", "pt-BR", "br"),
        NovelCool("https://fr.novelcool.com", "fr"),
    )
}
