package eu.kanade.tachiyomi.extension.en.mangareadercc
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.paprika.PaprikaAlt

class MangaReaderCC : PaprikaAlt("MangaReader.cc", "https://www.mangareader.cc", "en")
