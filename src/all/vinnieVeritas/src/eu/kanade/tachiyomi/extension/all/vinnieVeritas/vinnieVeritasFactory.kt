package eu.kanade.tachiyomi.extension.all.vinnieVeritas

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class vinnieVeritasFactory : SourceFactory {

    override fun createSources(): List<Source> =
        listOf(
            vinnieVeritas("en"),
            vinnieVeritas("es"),
        )
}
