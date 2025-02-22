package eu.kanade.tachiyomi.extension.en.readnarutoborutosamurai8mangaonline
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadNarutoBorutoSamurai8MangaOnline : MangaCatalog("Read Naruto Boruto Samurai 8 Manga Online", "https://ww7.readnaruto.com", "en") {
    override val sourceList = listOf(
        Pair("Boruto", "$baseUrl/manga/boruto-naruto-next-generations/"),
        Pair("Naruto", "$baseUrl/manga/naruto/"),
        Pair("Colored", "$baseUrl/manga/naruto-digital-colored-comics/"),
        Pair("Naruto Gaiden", "$baseUrl/manga/naruto-gaiden-the-seventh-hokage/"),
        Pair("Samurai 8", "$baseUrl/manga/samurai-8-hachimaru-den/"),
    ).sortedBy { it.first }.distinctBy { it.second }
}
