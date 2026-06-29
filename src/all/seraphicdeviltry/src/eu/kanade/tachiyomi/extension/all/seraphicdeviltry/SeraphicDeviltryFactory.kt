package eu.kanade.tachiyomi.extension.all.seraphicdeviltry

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class SeraphicDeviltryFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        SeraphicDeviltry("en", "https://seraphic-deviltry.com"),
        SeraphicDeviltry("es", "https://spanish.seraphic-deviltry.com"),
    )
}
