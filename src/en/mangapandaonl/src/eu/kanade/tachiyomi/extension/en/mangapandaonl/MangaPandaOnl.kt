package eu.kanade.tachiyomi.extension.en.mangapandaonl
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub

class MangaPandaOnl : MangaHub("MangaPanda.onl", "https://mangapanda.onl", "en", "mr02")
