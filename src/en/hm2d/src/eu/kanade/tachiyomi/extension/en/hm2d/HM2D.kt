package eu.kanade.tachiyomi.extension.en.hm2d

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class HM2D : Madara(
    "HM2D",
    "https://mangadistrict.com/hdoujin/",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
) {

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url.removePrefix("/hdoujin/"), headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url.removePrefix("/hdoujin/"), headers)
    }
}
