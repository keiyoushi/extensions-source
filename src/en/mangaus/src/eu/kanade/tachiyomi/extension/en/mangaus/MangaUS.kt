package eu.kanade.tachiyomi.extension.en.mangaus

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaUS : Madara("MangaUS", "https://mangaus.xyz", "en") {
    override val pageListParseSelector = "img"
}
