package eu.kanade.tachiyomi.extension.tr.korelimanga

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Response

@Source
abstract class KoreliManga : InitManga() {

    override val mangaUrlDirectory = "manga"
    override val popularUrlSlug = "trending-manga"
    override val latestUrlSlug = "recently-updated"

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        val baseUrlReq = response.request.url
        var page = 2

        do {
            val items = document.select(chapterListSelector())
            if (items.isEmpty()) break

            chapters.addAll(items.map(::chapterFromElement))

            val nextUrl = baseUrlReq.newBuilder()
                .addPathSegment("chapter")
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .build()

            page++

            document = client.newCall(GET(nextUrl, headers)).execute().use { nextResponse ->
                nextResponse.asJsoup()
            }

            val hasNextPage = document.selectFirst("ul.uk-pagination a:not(:matchesOwn(\\S))[href^=http]") != null
        } while (hasNextPage)

        return chapters
    }
}
