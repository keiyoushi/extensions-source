package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NiaddFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Niadd("Br", "https://br.niadd.com", "pt-BR"),
        Niadd("En", "https://www.niadd.com", "en"),
        Niadd("Es", "https://es.niadd.com", "es"),
        Niadd("It", "https://it.niadd.com", "it"),
        Niadd("Ru", "https://ru.niadd.com", "ru"),
        Niadd("De", "https://de.niadd.com", "de"),
        Niadd("Fr", "https://fr.niadd.com", "fr"),
    )
}
