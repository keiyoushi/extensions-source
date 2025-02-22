package eu.kanade.tachiyomi.extension.ko.newtoki
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class TokiFactory : SourceFactory {
    override fun createSources() = listOf(ManaToki, NewTokiWebtoon)
}
