package eu.kanade.tachiyomi.extension.all.comicklive

import eu.kanade.tachiyomi.source.SourceFactory

class ComickFactory : SourceFactory {
    override fun createSources() = listOf(Comick("en"))
}
