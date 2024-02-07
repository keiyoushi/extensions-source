package eu.kanade.tachiyomi.extension.all.comikey

import eu.kanade.tachiyomi.source.SourceFactory

class ComikeyFactory : SourceFactory {
    override fun createSources() = listOf(
        Comikey("en"),
        Comikey("id"),
        Comikey("pt-BR"),
        Comikey("es"),
    )
}
