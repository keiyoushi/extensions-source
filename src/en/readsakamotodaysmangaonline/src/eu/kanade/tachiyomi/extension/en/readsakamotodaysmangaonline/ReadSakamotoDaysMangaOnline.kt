package eu.kanade.tachiyomi.extension.en.readsakamotodaysmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadSakamotoDaysMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        Pair("Sakamoto Days", "$baseUrl/manga/sakamoto-days/"),
        Pair("Sakamoto Days Digital Colored Comics", "$baseUrl/manga/sakamoto-days-digital-colored-comics/"),
        Pair("Sakamoto Days: Assassin's Method", "$baseUrl/manga/sakamoto-days-assassins-method/"),
        Pair("Garaku", "$baseUrl/manga/garaku/"),
        Pair("Locker Room", "$baseUrl/manga/locker-room/"),
    )
}
