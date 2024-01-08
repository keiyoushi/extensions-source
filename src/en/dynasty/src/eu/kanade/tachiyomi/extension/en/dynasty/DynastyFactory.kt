package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class DynastyFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllDynasty()
}

fun getAllDynasty() =
    listOf(
        DynastyAnthologies(),
        DynastyChapters(),
        DynastyDoujins(),
        DynastyIssues(),
        DynastySeries(),
    )
