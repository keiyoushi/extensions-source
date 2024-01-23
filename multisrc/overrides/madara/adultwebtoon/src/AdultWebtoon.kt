package eu.kanade.tachiyomi.extension.en.adultwebtoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.CacheControl
import okhttp3.Request

class AdultWebtoon : Madara("Adult Webtoon", "https://adultwebtoon.com", "en") {
    override val mangaSubString = "adult-webtoon"
}
