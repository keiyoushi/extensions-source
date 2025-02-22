package eu.kanade.tachiyomi.extension.all.manga18me
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class M18MFactory : SourceFactory {
    override fun createSources(): List<Source> =
        listOf(
            Manga18Me("all"),
            Manga18Me("en"),
        )
}
