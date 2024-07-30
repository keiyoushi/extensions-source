package eu.kanade.tachiyomi.extension.en.manhwahentaime

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Response

class ManhwahentaiMe : Madara("Manhwahentai.me", "https://manhwahentai.me", "en") {

    override val useNewChapterEndpoint: Boolean = true

    override val mangaSubString = "webtoon"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        launchIO { countViews(document) }

        val comicObj = document.selectFirst("script:containsData(comicObj)")!!.data()
        val id = comicObj.filter { it.isDigit() }
        val name = comicObj.substringBefore(":").substringAfter("{").trim()
        val ajax_url = document.selectFirst("script:containsData(ajax)")!!.data().substringAfter('"').substringBefore('"')

        val body = FormBody.Builder()
            .add(name, id)
            .add("action", "ajax_chap")
            .build()
        val doc = client.newCall(POST(ajax_url, headers, body)).execute().asJsoup()
        val chapterElements = doc.select(chapterListSelector())

        return chapterElements.map(::chapterFromElement)
    }
    override fun searchMangaSelector() = "div.page-item-detail"
}
