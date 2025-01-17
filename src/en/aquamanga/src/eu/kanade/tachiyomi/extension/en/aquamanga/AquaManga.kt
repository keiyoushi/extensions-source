package eu.kanade.tachiyomi.extension.en.aquamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class AquaManga : Madara("Aqua Manga", "https://aquareader.net", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .set("Accept-Language", "en-US,en;q=0.5")
        .set("Referer", "$baseUrl/")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Upgrade-Insecure-Requests", "1")
        .set("X-Requested-With", "org.chromium.chrome")

    override val chapterUrlSuffix = ""
}
