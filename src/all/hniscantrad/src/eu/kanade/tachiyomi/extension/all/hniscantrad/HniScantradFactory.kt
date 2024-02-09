package eu.kanade.tachiyomi.extension.all.hniscantrad

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class HniScantradFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HniScantrad("en"),
        HniScantrad("fr"),
    )
}
