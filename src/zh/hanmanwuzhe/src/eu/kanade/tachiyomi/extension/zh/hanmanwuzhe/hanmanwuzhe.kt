package eu.kanade.tachiyomi.extension.zh.hanmanwuzhe

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class hanmanwuzhe : MCCMS(
    "嘀嗒漫画",
    "https://www.hanmanwuzhe.com",
    "zh",
    MCCMSConfig(useMobilePageList = true),
) {

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.chapterbox li.pic img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.attr("src"))
        }
    }
}
