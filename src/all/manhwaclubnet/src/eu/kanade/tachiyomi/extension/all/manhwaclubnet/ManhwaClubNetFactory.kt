package eu.kanade.tachiyomi.extension.all.manhwaclubnet
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class ManhwaClubNetFactory : SourceFactory {
    // Font for icon: Cooper BT Std Black Headline
    override fun createSources() = listOf(
        ManhwaClubNet("en"),
        ManhwaClubNet("ko"),
    )
}
