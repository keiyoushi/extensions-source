package eu.kanade.tachiyomi.extension.all.mangadna

import eu.kanade.tachiyomi.source.SourceFactory

class MangaDNAFactory : SourceFactory {
    override fun createSources() = listOf(
        MangaDNA("en"),
        MangaDNA("all"),
    )
}
