package eu.kanade.tachiyomi.extension.ko.navercomic
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NaverComicFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NaverWebtoon(),
        NaverBestChallenge(),
        NaverChallenge(),
    )
}
