package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaPlusFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaPlus("en", "eng", Language.ENGLISH),
        MangaPlus("es", "esp", Language.SPANISH),
        MangaPlus("fr", "fra", Language.FRENCH),
        MangaPlus("id", "ind", Language.INDONESIAN),
        MangaPlus("pt-BR", "ptb", Language.PORTUGUESE_BR),
        MangaPlus("ru", "rus", Language.RUSSIAN),
        MangaPlus("th", "tha", Language.THAI),
        MangaPlus("vi", "vie", Language.VIETNAMESE),
    )
}
