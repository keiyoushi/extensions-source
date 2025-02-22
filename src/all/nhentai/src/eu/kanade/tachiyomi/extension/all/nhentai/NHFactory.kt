package eu.kanade.tachiyomi.extension.all.nhentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NHFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NHentai("en", "english"),
        NHentai("ja", "japanese"),
        NHentai("zh", "chinese"),
        NHentai("all", ""),
    )
}
