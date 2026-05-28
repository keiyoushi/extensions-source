package eu.kanade.tachiyomi.extension.ko.ntk

import eu.kanade.tachiyomi.source.SourceFactory

class NTKFactory : SourceFactory {
    override fun createSources() = listOf(
        NTKManga(),
        NTKWebtoon(),
    )
}
