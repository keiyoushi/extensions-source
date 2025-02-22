package eu.kanade.tachiyomi.extension.all.cubari
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class CubariFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Cubari("en"),
        Cubari("all"),
        Cubari("other"),
    )
}
