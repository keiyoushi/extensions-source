package ar.procomic

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ProComicFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ProComic()
    )
}
