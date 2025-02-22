package eu.kanade.tachiyomi.extension.en.vizshonenjump
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class VizFactory : SourceFactory {
    override fun createSources() = listOf(
        Viz("VIZ Shonen Jump", "shonenjump"),
        Viz("VIZ Manga", "vizmanga"),
    )
}
