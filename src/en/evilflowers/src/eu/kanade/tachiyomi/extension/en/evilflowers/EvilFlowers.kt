package eu.kanade.tachiyomi.extension.en.evilflowers
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class EvilFlowers : Madara(
    "Evil Flowers",
    "https://evilflowers.com",
    "en",
) {
    override val versionId = 2

    override val mangaSubString = "project"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
