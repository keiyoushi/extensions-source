package eu.kanade.tachiyomi.extension.zh.bakamh

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Locale

class Bakamh : Madara(
    "巴卡漫画",
    "https://bakamh.com",
    "zh",
    SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINESE),
) {
    override val mangaDetailsSelectorStatus = ".post-content_item:contains(状态) .summary-content"

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder()
            .add("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
    }
}
