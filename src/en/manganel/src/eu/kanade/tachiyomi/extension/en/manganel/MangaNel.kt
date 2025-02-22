package eu.kanade.tachiyomi.extension.en.manganel
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

class MangaNel : MangaHub("MangaNel", "https://manganel.me", "en", "mn05")
