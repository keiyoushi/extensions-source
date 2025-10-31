package eu.kanade.tachiyomi.extension.en.readnarutoborutosamurai8mangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadNarutoBorutoSamurai8MangaOnline : MangaCatalog("Read Naruto Boruto Samurai 8 Manga Online", "https://ww11.readnaruto.com", "en") {
    override val sourceList = listOf(
        Pair("Boruto - Two Blue Vortex", "$baseUrl/manga/boruto-two-blue-vortex/"),
        Pair("Naruto", "$baseUrl/manga/naruto/"),
        Pair("Naruto Colored", "$baseUrl/manga/naruto-digital-colored-comics/"),
        Pair("Naruto Gaiden", "$baseUrl/manga/naruto-gaiden-the-seventh-hokage/"),
        Pair("Boruto", "$baseUrl/manga/boruto-naruto-next-generations/"),
        Pair("Samurai 8", "$baseUrl/manga/samurai-8-hachimaru-den/"),
        Pair("Rock Lee SpinOff", "$baseUrl/manga/rock-lee-no-seishun-full-power-ninden/"),
        Pair("Chibi Sasuke", "$baseUrl/manga/uchiha-sasuke-no-sharingan-den/"),
        Pair("Sasuke Story", "$baseUrl/manga/naruto-sasuke-retsuden-uchiha-no-matsuei-to-tenkyuu-no-hoshikuzu/"),
    )
}
