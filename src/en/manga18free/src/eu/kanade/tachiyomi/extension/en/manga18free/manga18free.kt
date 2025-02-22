package eu.kanade.tachiyomi.extension.en.manga18free
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class manga18free : Madara(
    "Manga18Free",
    "https://manga18free.com",
    "en",
) {
    override fun searchMangaNextPageSelector() = "a.nextpostslink"
}
