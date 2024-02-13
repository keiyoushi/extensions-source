package eu.kanade.tachiyomi.extension.all.mangareaderto

import eu.kanade.tachiyomi.source.SourceFactory

class MangaReaderFactory : SourceFactory {
    override fun createSources() =
        arrayOf("en", "fr", "ja", "ko", "zh").map(::MangaReader)
}
