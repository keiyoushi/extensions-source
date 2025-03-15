package eu.kanade.tachiyomi.extension.pt.osakascan

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class OsakaScan : ZeistManga(
    "Osaka Scan",
    "https://www.osakascan.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val popularMangaSelector = "#PopularPosts2 article"
    override val popularMangaSelectorTitle = "h3 a"
    override val popularMangaSelectorUrl = popularMangaSelectorTitle

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst(mangaDetailsSelectorDescription)?.text()
        document.selectFirst("span[data-status]")?.text()?.let {
            status = parseStatus(it)
        }
        genre = document.select("dt:contains(Gênero) + dd a").joinToString { it.text() }
        setUrlWithoutDomain(document.location())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response)
            .sortedBy(SChapter::chapter_number)
    }
}
