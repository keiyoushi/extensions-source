package eu.kanade.tachiyomi.extension.tr.eldermanga

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import okhttp3.Headers

class ElderManga : UzayManga(
    "Elder Manga",
    "https://eldermanga.com",
    lang = "tr",
    versionId = 1,
    cdnUrl = "https://eldermangacdn2.efsaneler.can.re",
) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        .set("Accept", "application/json, text/plain, */*")
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
}
