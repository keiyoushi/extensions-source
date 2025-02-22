package eu.kanade.tachiyomi.extension.all.izneo
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

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
