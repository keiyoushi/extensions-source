package eu.kanade.tachiyomi.extension.all.holonometria
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class HolonometriaFactory : SourceFactory {
    override fun createSources() = listOf(
        Holonometria("ja", ""),
        Holonometria("en"),
        Holonometria("id"),
    )
}
