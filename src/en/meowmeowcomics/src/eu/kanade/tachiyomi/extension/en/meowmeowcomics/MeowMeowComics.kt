package eu.kanade.tachiyomi.extension.en.meowmeowcomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response

class MeowMeowComics : Madara(
    "Meow Meow Comics",
    "https://meowmeowcomics.com",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        return xhrChaptersRequest(baseUrl + manga.url.removeSuffix("/"))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup()
            .select("ul.main > li.parent,ul.main:not(:has(>li.parent))")
            .sortedByDescending { it.selectFirst("a.has-child")?.text()?.toIntOrNull() ?: 0 }
            .flatMap { season ->
                season.select(chapterListSelector()).map(::chapterFromElement)
            }
    }
}
