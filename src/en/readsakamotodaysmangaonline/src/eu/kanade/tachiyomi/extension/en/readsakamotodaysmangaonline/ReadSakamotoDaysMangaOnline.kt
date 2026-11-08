package eu.kanade.tachiyomi.extension.en.readsakamotodaysmangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog
import keiyoushi.annotation.Source

@Source
abstract class ReadSakamotoDaysMangaOnline : MangaCatalog() {
    override val sourceList = listOf(
        "Sakamoto Days" to "$baseUrl/manga/sakamoto-days/",
        "Sakamoto Days Digital Colored Comics" to "$baseUrl/manga/sakamoto-days-digital-colored-comics/",
        "Sakamoto Days: Assassin's Method" to "$baseUrl/manga/sakamoto-days-assassins-method/",
        "Sakamoto Days Official Fan Book" to "$baseUrl/manga/sakamoto-days-official-fan-book/",
        "Sakamoto Holidays" to "$baseUrl/manga/sakamoto-holidays/",
        "Garaku" to "$baseUrl/manga/garaku/",
        "Locker Room" to "$baseUrl/manga/locker-room/",
    )
}
