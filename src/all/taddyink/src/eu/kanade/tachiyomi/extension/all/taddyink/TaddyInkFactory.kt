package eu.kanade.tachiyomi.extension.all.taddyink
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class TaddyInkFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        TaddyInk("all", ""),
    )
}
