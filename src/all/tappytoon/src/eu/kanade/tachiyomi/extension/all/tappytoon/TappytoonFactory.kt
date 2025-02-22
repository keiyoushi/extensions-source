package eu.kanade.tachiyomi.extension.all.tappytoon
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.SourceFactory

class TappytoonFactory : SourceFactory {
    private val langs = setOf("en", "fr", "de")

    override fun createSources() = langs.map(::Tappytoon)
}
