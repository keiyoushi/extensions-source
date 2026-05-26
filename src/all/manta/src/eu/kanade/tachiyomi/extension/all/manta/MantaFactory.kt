package eu.kanade.tachiyomi.extension.all.manta

import eu.kanade.tachiyomi.source.SourceFactory

class MantaFactory : SourceFactory {
    override fun createSources() = listOf(
        MantaComics("en", "https://manta.net"),
        MantaComics("es", "https://manta.net/es"),
    )
}
