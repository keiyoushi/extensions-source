package eu.kanade.tachiyomi.extension.all.manta

import eu.kanade.tachiyomi.source.SourceFactory

class MantaFactory : SourceFactory {
    override fun createSources() = listOf(
        Manta("en"),
        Manta("es"),
    )
}
