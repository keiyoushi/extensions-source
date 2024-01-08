package eu.kanade.tachiyomi.extension.all.nhentai

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NHFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NHentai("en", "english"),
        NHentai("ja", "japanese"),
        NHentai("zh", "chinese"),
        NHentai("all", ""),
    )
}
