package eu.kanade.tachiyomi.extension.all.cubari

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class CubariFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Cubari("en"),
        Cubari("all"),
        Cubari("other"),
    )
}
