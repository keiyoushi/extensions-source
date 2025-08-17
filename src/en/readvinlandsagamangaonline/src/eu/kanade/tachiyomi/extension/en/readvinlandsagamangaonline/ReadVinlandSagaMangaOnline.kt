package eu.kanade.tachiyomi.extension.en.readvinlandsagamangaonline

import eu.kanade.tachiyomi.multisrc.mangacatalog.MangaCatalog

class ReadVinlandSagaMangaOnline : MangaCatalog("Read Vinland Saga Manga Online", "https://ww5.readvinlandsaga.com", "en") {
    override val sourceList = listOf(
        Pair("Vinland Saga", "$baseUrl/manga/vinland-saga/"),
        Pair("Sayonara ga Chikai no de", "$baseUrl/manga/sayonara-ga-chikai-no-de/"),
        Pair("Fan Colored", "$baseUrl/manga/vinland-saga-colored/"),
        Pair("Planetes", "$baseUrl/manga/planetes/"),
    )
}
