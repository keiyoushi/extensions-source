package eu.kanade.tachiyomi.extension.tr.jiangzaitoon
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Jiangzaitoon : Madara(
    "Jiangzaitoon",
    "https://jiangzaitoon.kim",
    "tr",
    SimpleDateFormat("d MMM yyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val client: OkHttpClient by lazy {
        super.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.MINUTES) // aka shit source
            .build()
    }

    override val chapterUrlSelector = "> a"
}
