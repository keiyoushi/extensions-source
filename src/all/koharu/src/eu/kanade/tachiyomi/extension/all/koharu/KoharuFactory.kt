package eu.kanade.tachiyomi.extension.all.koharu

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class KoharuFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Koharu(),
        Koharu("en", "english", 1484902275639232927),
        Koharu("ja", "japanese", 2439015869566675873),
    )
}
