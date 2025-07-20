package eu.kanade.tachiyomi.extension.es.lectormonline

import eu.kanade.tachiyomi.source.SourceFactory

class LectorMOnlineFactory : SourceFactory {
    override fun createSources() = listOf(
        LectorMOnline("Lector MOnline", "https://www.lectormangas.online", "es"),
        LectorMOnline("MangasX", "https://mangasx.online", "es"),
    )
}
