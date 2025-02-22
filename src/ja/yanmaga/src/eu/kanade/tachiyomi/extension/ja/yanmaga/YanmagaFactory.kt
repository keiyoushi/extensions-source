package eu.kanade.tachiyomi.extension.ja.yanmaga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class YanmagaFactory : SourceFactory {
    override fun createSources() = listOf(
        YanmagaComics(),
        YanmagaGravures(),
    )
}
