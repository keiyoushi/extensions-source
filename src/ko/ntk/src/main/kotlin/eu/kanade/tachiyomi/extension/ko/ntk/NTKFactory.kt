package eu.kanade.tachiyomi.extension.ko.ntk

import eu.kanade.tachiyomi.source.SourceFactory

// Registers both sources with Mihon
class NTKFactory : SourceFactory {
    override fun createSources() = listOf(
        NTKManga(),
        NTKWebtoon(),
    )
}
