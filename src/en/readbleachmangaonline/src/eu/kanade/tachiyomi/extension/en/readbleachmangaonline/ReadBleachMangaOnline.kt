package eu.kanade.tachiyomi.extension.en.readbleachmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadBleachMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        Pair("Bleach", "$baseUrl/manga/bleach/"),
        Pair("Bleach Digital Colored Comics", "$baseUrl/manga/bleach-digital-colored-comics/"),
        Pair("Bleach: The Unforgivens", "$baseUrl/manga/bleach-the-unforgivens/"),
        Pair("Bleach: Can't Fear Your Own World", "$baseUrl/manga/bleach-cant-fear-your-own-world/"),
        Pair("Bleach Unmasked Short Stories", "$baseUrl/manga/bleach-unmasked-short-stories/"),
        Pair("Bleach One Shot: Burn the Witch", "$baseUrl/manga/bleach-one-shot-burn-the-witch/"),
        Pair("Zombie Powder", "$baseUrl/manga/zombie-powder/"),
    )
}
