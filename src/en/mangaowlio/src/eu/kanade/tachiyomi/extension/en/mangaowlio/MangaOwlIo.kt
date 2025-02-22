package eu.kanade.tachiyomi.extension.en.mangaowlio
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaOwlIo : Madara("MangaOwl.io (unoriginal)", "https://mangaowl.io", "en") {
    override val useNewChapterEndpoint = true
}
