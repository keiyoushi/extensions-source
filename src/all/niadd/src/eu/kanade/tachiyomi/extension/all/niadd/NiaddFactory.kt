package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.source.SourceFactory

class NiaddFactory : SourceFactory {
    override fun createSources() = listOf(
        NiaddEn()
    )
}
