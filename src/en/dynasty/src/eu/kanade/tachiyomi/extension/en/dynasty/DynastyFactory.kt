package eu.kanade.tachiyomi.extension.en.dynasty
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class DynastyFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllDynasty()
}

fun getAllDynasty() =
    listOf(
        DynastyAnthologies(),
        DynastyChapters(),
        DynastyDoujins(),
        DynastyIssues(),
        DynastySeries(),
        DynastyScanlator(),
    )
