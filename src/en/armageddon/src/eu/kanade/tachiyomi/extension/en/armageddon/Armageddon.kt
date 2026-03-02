package eu.kanade.tachiyomi.extension.en.armageddon

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import org.jsoup.nodes.Document

class Armageddon :
    MangaThemesia(
        name = "Armageddon",
        baseUrl = "https://www.silentquill.net",
        lang = "en",
    ) {
    override fun pageListParse(document: Document): List<Page> {
        val scriptContent = document.selectFirst("script:containsData(WyJodHRw)")?.data()
            ?: return super.pageListParse(document)
        val base64Match = Regex("""['"](WyJodHRw[\w+/=]+)['"]""").find(scriptContent)?.groupValues?.get(1)

        return if (base64Match != null) {
            val decoded = String(Base64.decode(base64Match, Base64.DEFAULT))
            val images = decoded.parseAs<List<String>>()
            images.mapIndexed { i, img ->
                Page(i, document.location(), img)
            }
        } else {
            super.pageListParse(document)
        }
    }
}
