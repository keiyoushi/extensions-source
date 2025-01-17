package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.source.SourceFactory

class MangaFireFactory : SourceFactory {
    override fun createSources() = listOf(
        MangaFire("en"),
        MangaFire("es"),
        MangaFire("es-419", "es-la"),
        MangaFire("fr"),
        MangaFire("ja"),
        MangaFire("pt"),
        MangaFire("pt-BR", "pt-br"),
    )
}
