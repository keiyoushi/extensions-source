package eu.kanade.tachiyomi.extension.en.igniscomic
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class IgnisComic : MangaThemesia(
    "Ignis Comic",
    "https://manhuaga.com",
    "en",
)
