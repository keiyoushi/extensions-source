package eu.kanade.tachiyomi.extension.pt.cerisescans

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class CeriseScan : Madara(
    "Cerise Scan",
    "https://loverstoon.com",
    "pt-BR",
    SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
) {
    override val versionId: Int = 3

    override val client = super.client.newBuilder()
        .rateLimit(3, 2)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true

    override fun pageListParse(document: Document): List<Page> {
        return document.selectFirst(".page-break a")!!.attr("href")
            .substringAfter("auth=")
            .let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
            .parseAs<PageDto>()
            .toPageList()
    }

    @Serializable
    private class PageDto(private val url: String) {
        fun toPageList(): List<Page> = url.split(";").mapIndexed { index, image ->
            Page(index, imageUrl = image)
        }
    }
}
