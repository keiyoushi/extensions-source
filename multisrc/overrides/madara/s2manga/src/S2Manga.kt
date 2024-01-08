package eu.kanade.tachiyomi.extension.en.s2manga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class S2Manga : Madara("S2Manga", "https://www.s2manga.com", "en") {

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val pageListParseSelector = "div.page-break img[src*=\"https\"]"
}
