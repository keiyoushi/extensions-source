package eu.kanade.tachiyomi.extension.all.unionmangas

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class UnionMangasFactory : SourceFactory {
    override fun createSources(): List<Source> = languages.map { UnionMangas(it) }
}
