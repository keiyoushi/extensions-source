package eu.kanade.tachiyomi.extension.en.mangafre

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangafreFactory : SourceFactory {
    /**
     * Create a new copy of the sources
     * @return The created sources
     */
    override fun createSources(): List<Source> {
        return listOf(
            Mangafre(),
            MangafreId(),
            MangafreZh(),
        )
    }
}

class Mangafre : MangafreGlobal("en")
class MangafreId : MangafreGlobal("id")
class MangafreZh : MangafreGlobal("zh", supportsSearch = false)
