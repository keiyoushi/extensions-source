package eu.kanade.tachiyomi.extension.es.begatranslation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class BegaTranslation :
    Madara(
        "Bega Translation",
        "https://begatranslation.com",
        "es",
        SimpleDateFormat("dd MMMM, yyyy", Locale("es")),
    ) {
    override val useNewChapterEndpoint = true
    override val mangaSubString = "series"

    override fun popularMangaFromElement(element: Element): SManga = super.popularMangaFromElement(element).apply {
        thumbnail_url = thumbnail_url?.replaceFirst("-175x238", "")
    }

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).apply {
        thumbnail_url = thumbnail_url?.replaceFirst("-193x278", "")
    }

    private val rkJsonRegex = """(?s)var\s+RK\s*=\s*(\{.*?\});""".toRegex()

    override fun pageListParse(document: Document): List<Page> {
        val pages = super.pageListParse(document)
        if (pages.isNotEmpty()) return pages

        val scriptData = document.selectFirst("script#rk-main-js-extra")?.data()
            ?: throw Exception("Could not find chapter data")

        val rkData = rkJsonRegex.find(scriptData)?.groupValues?.get(1)?.parseAs<RkDto>()
            ?: throw Exception("Could not parse chapter data")

        val formBody = FormBody.Builder()
            .add("rt", rkData.rt)
            .add("chapter_id", rkData.chapterId)
            .add("manga_id", rkData.mangaId)
            .add("slug", rkData.slug)
            .add("rk_show_back", rkData.showBack)
            .build()

        val readerLanding = client.newCall(POST(rkData.readerApi, headers, formBody)).execute().asJsoup()

        val readerBtn = readerLanding.selectFirst("a.rk-btn-read")

        val chapterReader = if (readerBtn != null) {
            val readerUrl = readerBtn.absUrl("href")
            val readerHeaders = headersBuilder()
                .set("Referer", readerLanding.location())
                .build()

            client.newCall(GET(readerUrl, readerHeaders)).execute().asJsoup()
        } else {
            readerLanding
        }

        return chapterReader.select("img.rk-img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    @Serializable
    class RkDto(
        val readerApi: String,
        val rt: String,
        val chapterId: String,
        val mangaId: String,
        val slug: String,
        val showBack: String,
    )
}
