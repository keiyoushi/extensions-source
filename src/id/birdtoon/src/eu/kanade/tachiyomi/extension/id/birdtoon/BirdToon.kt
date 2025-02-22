package eu.kanade.tachiyomi.extension.id.birdtoon
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BirdToon : Madara(
    "BirdToon",
    "https://birdtoon.org",
    "id",
) {
    override val mangaSubString = "komik"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
