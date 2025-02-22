package eu.kanade.tachiyomi.extension.all.manga18me

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class M18MFactory : SourceFactory {
    override fun createSources(): List<Source> =
        listOf(
            Manga18Me("all"),
            Manga18Me("en"),
        )
}
