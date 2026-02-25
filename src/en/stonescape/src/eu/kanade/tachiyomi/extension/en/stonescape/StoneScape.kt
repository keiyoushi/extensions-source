package eu.kanade.tachiyomi.extension.en.stonescape

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class StoneScape :
    Madara(
        "StoneScape",
        "https://stonescape.xyz",
        "en",
        SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH),
    ) {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 500 && response.request.url.toString().contains("/$mangaSubString/")) {
                response.newBuilder().code(200).build()
            } else {
                response
            }
        }
        .build()

    override val mangaSubString = "series"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorDescription = ".manga-summary"

    override val chapterUrlSelector = "li > a"

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
