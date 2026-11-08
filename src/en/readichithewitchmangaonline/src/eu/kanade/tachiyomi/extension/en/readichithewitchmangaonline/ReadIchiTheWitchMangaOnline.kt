package eu.kanade.tachiyomi.extension.en.readichithewitchmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadIchiTheWitchMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        "Ichi the Witch" to "$baseUrl/manga/ichi-the-witch/",
    )
}
