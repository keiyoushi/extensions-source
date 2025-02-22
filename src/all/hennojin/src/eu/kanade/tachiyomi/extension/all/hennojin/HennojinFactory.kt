package eu.kanade.tachiyomi.extension.all.hennojin
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class HennojinFactory : SourceFactory {
    override fun createSources() = listOf(
        Hennojin("en"),
        Hennojin("ja"),
    )
}
