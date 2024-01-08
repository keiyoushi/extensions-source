package eu.kanade.tachiyomi.extension.all.hennojin

import eu.kanade.tachiyomi.source.SourceFactory

class HennojinFactory : SourceFactory {
    override fun createSources() = listOf(
        Hennojin("en", ""),
        Hennojin("ja", "?archive=raw"),
    )
}
