package eu.kanade.tachiyomi.extension.all.vinnieVeritas

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class VinnieVeritasFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        VinnieVeritas("en"),
        VinnieVeritas("es"),
    )
}
