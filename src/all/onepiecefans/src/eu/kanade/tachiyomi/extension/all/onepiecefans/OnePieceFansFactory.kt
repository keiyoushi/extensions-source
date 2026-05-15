package eu.kanade.tachiyomi.extension.all.onepiecefans

import eu.kanade.tachiyomi.source.SourceFactory

class OnePieceFansFactory : SourceFactory {
    override fun createSources() = listOf(
        OnePieceFans("es", "es"),
        OnePieceFans("en", "en"),
    )
}
