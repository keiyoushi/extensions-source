package eu.kanade.tachiyomi.extension.es.templescanesp

import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response

class TempleScanEsp : MangaEsp(
    "Temple Scan",
    "https://templescanesp.caserosvive.com.ar",
    "es",
    apiBaseUrl = "https://apis.templescanesp.net",
) {

    // Site moved from custom theme to MangaEsp
    override val versionId = 3

    override fun mangaDetailsRequest(manga: SManga): Request {
        return super.mangaDetailsRequest(manga.apply { url = "$url?allow=true" })
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun pageListRequest(chapter: SChapter): Request {
        return super.pageListRequest(chapter.apply { url = "$url?allow=true" })
    }

    override fun pageListParse(response: Response): List<Page> {
        var doc = response.asJsoup()
        val form = doc.selectFirst("body > form[method=post]")
        if (form != null) {
            val url = form.attr("action")
            val headers = headersBuilder().set("Referer", doc.location()).build()
            val body = FormBody.Builder()
            form.select("input").forEach {
                body.add(it.attr("name"), it.attr("value"))
            }
            doc = client.newCall(POST(url, headers, body.build())).execute().asJsoup()
        }
        return doc.select("main.contenedor.read img, main > img[src]").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }
}
