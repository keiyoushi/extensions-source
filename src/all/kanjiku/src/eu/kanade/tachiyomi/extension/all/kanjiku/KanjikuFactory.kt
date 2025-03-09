package eu.kanade.tachiyomi.extension.all.kanjiku

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class KanjikuFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Kanjiku("de", ""),
        Kanjiku("en", "eng."),
    )
}
