package eu.kanade.tachiyomi.extension.en.mangahubio
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

class MangaHubIo : MangaHub("MangaHub", "https://mangahub.io", "en", "m01")
