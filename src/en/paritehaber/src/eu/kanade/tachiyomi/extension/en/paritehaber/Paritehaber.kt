package eu.kanade.tachiyomi.extension.en.paritehaber
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Paritehaber : Madara(
    "Paritehaber",
    "https://www.paritehaber.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "ph"

    override val fetchGenres = false
    override val chapterUrlSuffix = ""
}
