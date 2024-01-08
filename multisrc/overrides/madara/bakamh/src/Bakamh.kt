package eu.kanade.tachiyomi.extension.zh.bakamh

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Bakamh : Madara(
    "巴卡漫画",
    "https://bakamh.com",
    "zh",
    SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINESE),
) {
    override val mangaDetailsSelectorStatus = ".post-content_item:contains(状态) .summary-content"
}
