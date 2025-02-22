package eu.kanade.tachiyomi.extension.all.vinnieVeritas
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class vinnieVeritasFactory : SourceFactory {

    override fun createSources(): List<Source> =
        listOf(
            vinnieVeritas("en"),
            vinnieVeritas("es"),
        )
}
