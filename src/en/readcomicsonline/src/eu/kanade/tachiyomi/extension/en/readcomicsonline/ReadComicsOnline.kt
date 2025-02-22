package eu.kanade.tachiyomi.extension.en.readcomicsonline
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS

class ReadComicsOnline : MMRCMS(
    "Read Comics Online",
    "https://readcomicsonline.ru",
    "en",
    itemPath = "comic",
    chapterString = "",
)
