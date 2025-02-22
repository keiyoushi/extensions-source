package eu.kanade.tachiyomi.extension.es.traduccionesmoonlight

import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Response

class TraduccionesMoonlight : MangaEsp(
    "Traducciones Moonlight",
    "https://traduccionesmoonlight.com",
    "es",
) {
    // Mangathemesia -> MangaEsp
    override val versionId = 3

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
