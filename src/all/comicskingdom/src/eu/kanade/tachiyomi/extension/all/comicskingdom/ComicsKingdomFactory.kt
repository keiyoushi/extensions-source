package eu.kanade.tachiyomi.extension.all.comicskingdom

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ComicsKingdomFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(ComicsKingdom("en"), ComicsKingdom("es"))
}
