package eu.kanade.tachiyomi.extension.all.koharu

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class KoharuFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Koharu(),
        Koharu("en", "english"),
        Koharu("ja", "japanese"),
        Koharu("zh", "chinese"),
    )
}
