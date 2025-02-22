package eu.kanade.tachiyomi.extension.all.commitstrip
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class CommitStripFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        CommitStripEnglish(),
        CommitStripFrench(),
    )
}

class CommitStripEnglish() : CommitStrip("en", "en")
class CommitStripFrench() : CommitStrip("fr", "fr")
