package eu.kanade.tachiyomi.extension.en.readbleachmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadBleachMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        "Bleach" to "$baseUrl/manga/bleach/",
        "Bleach Digital Colored Comics" to "$baseUrl/manga/bleach-digital-colored-comics/",
        "Bleach: The Unforgivens" to "$baseUrl/manga/bleach-the-unforgivens/",
        "Bleach: Can't Fear Your Own World" to "$baseUrl/manga/bleach-cant-fear-your-own-world/",
        "Bleach Unmasked Short Stories" to "$baseUrl/manga/bleach-unmasked-short-stories/",
        "Bleach One Shot: Burn the Witch" to "$baseUrl/manga/bleach-one-shot-burn-the-witch/",
        "Zombie Powder" to "$baseUrl/manga/zombie-powder/",
    )
}
