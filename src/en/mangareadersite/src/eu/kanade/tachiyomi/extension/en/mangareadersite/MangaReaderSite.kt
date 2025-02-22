package eu.kanade.tachiyomi.extension.en.mangareadersite
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

class MangaReaderSite : MangaHub("MangaReader.site", "https://mangareader.site", "en", "mr01")
