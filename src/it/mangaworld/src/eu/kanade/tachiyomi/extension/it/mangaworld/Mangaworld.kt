package eu.kanade.tachiyomi.extension.it.mangaworld
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangaworld.MangaWorld

class Mangaworld : MangaWorld("Mangaworld", "https://www.mangaworld.ac", "it")
