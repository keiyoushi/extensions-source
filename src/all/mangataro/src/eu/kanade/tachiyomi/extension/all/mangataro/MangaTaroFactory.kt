package eu.kanade.tachiyomi.extension.all.mangataro

import eu.kanade.tachiyomi.source.SourceFactory

class MangaTaroFactory : SourceFactory {
    override fun createSources() = listOf(
        MangaTaro("en"),
        MangaTaroGroup("pt-BR", groups = listOf(9)),
    )
}
