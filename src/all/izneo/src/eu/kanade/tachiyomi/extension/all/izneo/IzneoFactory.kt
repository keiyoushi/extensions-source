package eu.kanade.tachiyomi.extension.all.izneo

import eu.kanade.tachiyomi.source.SourceFactory

class IzneoFactory : SourceFactory {
    override fun createSources() = listOf(
        Izneo("en"),
        Izneo("fr"),
        // Izneo("de"),
        // Izneo("nl"),
        // Izneo("it"),
    )
}
