package eu.kanade.tachiyomi.extension.en.holymanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader

class HolyManga : FMReader(
    "HolyManga",
    "https://w34.holymanga.net",
    "en",
) {
    override val versionId = 2

    override val chapterUrlSelector = ""
}
