package eu.kanade.tachiyomi.extension.tr.mangatilkisi
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.FormBody
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTilkisi : Madara(
    "MangaTilkisi",
    "https://www.manga-tilkisi.com",
    "tr",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun pageListRequest(chapter: SChapter): Request {
        val payload = FormBody.Builder()
            .add("verified", "1")
            .build()
        return POST(chapter.url, headers, payload)
    }
}
