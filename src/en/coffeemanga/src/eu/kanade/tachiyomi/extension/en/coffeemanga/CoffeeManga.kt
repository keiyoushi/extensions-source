package eu.kanade.tachiyomi.extension.en.coffeemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class CoffeeManga : Madara("Coffee Manga", "https://coffeemanga.io", "en") {
    override val useNewChapterEndpoint = false
}
