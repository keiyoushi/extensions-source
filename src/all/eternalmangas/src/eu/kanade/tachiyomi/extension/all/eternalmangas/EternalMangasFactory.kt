package eu.kanade.tachiyomi.extension.all.eternalmangas

import eu.kanade.tachiyomi.source.SourceFactory

class EternalMangasFactory : SourceFactory {
    override fun createSources() = listOf(
        EternalMangasES(),
        EternalMangasEN(),
        EternalMangasPTBR(),
    )
}

class EternalMangasES : EternalMangas("es", "es")
class EternalMangasEN : EternalMangas("en", "en")
class EternalMangasPTBR : EternalMangas("pt-BR", "pt")
