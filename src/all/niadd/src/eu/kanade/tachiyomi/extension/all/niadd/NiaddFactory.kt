package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NiaddFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Niadd("https://br.niadd.com", "pt-BR"),
        Niadd("https://www.niadd.com", "en"),
        Niadd("https://es.niadd.com", "es"),
        Niadd("https://it.niadd.com", "it"),
        Niadd("https://ru.niadd.com", "ru"),
        Niadd("https://de.niadd.com", "de"),
        Niadd("https://fr.niadd.com", "fr"),
    )
}
