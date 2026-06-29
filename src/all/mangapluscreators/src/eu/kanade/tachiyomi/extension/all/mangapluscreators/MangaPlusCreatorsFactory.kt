package eu.kanade.tachiyomi.extension.all.mangapluscreators

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaPlusCreatorsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaPlusCreators("en"),
        MangaPlusCreators("es"),
    )
}
