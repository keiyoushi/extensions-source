package eu.kanade.tachiyomi.extension.tr.gafeland

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document

class Gafeland :
    MangaThemesia(
        "gafeland",
        "https://gafeland.com",
        "tr",
    ) {

    override fun pageListParse(document: Document): List<Page> {
        // dHNfcmVhZGVyLnJ1bih7 is "ts_reader.run({" in base64
        val script = document.selectFirst("script[src^=data:text/javascript;base64,dHNfcmVhZGVyLnJ1bih7]")
            ?: return super.pageListParse(document)
        val data = Base64.decode(script.attr("src").substringAfter("base64,"), Base64.DEFAULT).toString(Charsets.UTF_8)
        val imageListJson = JSON_IMAGE_LIST_REGEX.find(data)?.destructured?.toList()?.get(0).orEmpty()

        val imageList = try {
            imageListJson.parseAs<List<String>>()
        } catch (_: Exception) {
            emptyList()
        }

        return imageList.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }
}
