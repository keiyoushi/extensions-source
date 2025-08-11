package eu.kanade.tachiyomi.extension.en.readfairytailedenszeromangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadFairyTailEdensZeroMangaOnline : MangaCatalog("Read Fairy Tail & Edens Zero Manga Online", "https://ww8.readfairytail.com", "en") {
    override val sourceList = listOf(
        Pair("Eden's Zero", "$baseUrl/manga/edens-zero/"),
        Pair("Fairy Tail", "$baseUrl/manga/fairy-tail/"),
        Pair("FT Zero", "$baseUrl/manga/fairy-tail-zero/"),
        Pair("FT City Hero", "$baseUrl/manga/fairy-tail-city-hero/"),
        Pair("Hero’s", "$baseUrl/manga/heros/"),
        Pair("FT Happy Adv", "$baseUrl/manga/fairy-tail-happys-grand-adventure/"),
        Pair("FT 100 Year", "$baseUrl/manga/fairy-tail-100-years-quest/"),
        Pair("FT Ice Trail", "$baseUrl/manga/fairy-tail-ice-trail/"),
        Pair("FT x Taizai", "$baseUrl/manga/fairy-tail-x-nanatsu-no-taizai-christmas-special/"),
        Pair("Parasyte x FT", "$baseUrl/manga/parasyte-x-fairy-tail/"),
        Pair("Gaiden 1", "$baseUrl/manga/fairy-tail-gaiden-raigo-issen/"),
        Pair("FT x Rave", "$baseUrl/manga/fairy-tail-x-rave/"),
        Pair("Monster Hunter", "$baseUrl/manga/monster-hunter-orage/"),
        Pair("Rave Master", "$baseUrl/manga/rave-master/"),
        Pair("Dead Rock", "$baseUrl/manga/dead-rock/"),
        Pair("Fairy Girls", "$baseUrl/manga/fairy-girls/"),
        Pair("Gaiden 4", "$baseUrl/manga/fairy-tail-gaiden-raigo-issen/"),
        Pair("Gaiden 2", "$baseUrl/manga/fairy-tail-gaiden-kengami-no-souryuu/"),
        Pair("Gaiden 3", "$baseUrl/manga/fairy-tail-gaiden-road-knight/"),
        Pair("FT x 7DS", "$baseUrl/manga/fairy-tail-x-nanatsu-no-taizai-christmas-special/"),
    )
}
