package eu.kanade.tachiyomi.extension.es.lectorjpg
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp

class LectorJpg : MangaEsp(
    "LectorJPG",
    "https://lectorjpg.com",
    "es",
    "https://apis.pichulasjpg.xyz",
)
