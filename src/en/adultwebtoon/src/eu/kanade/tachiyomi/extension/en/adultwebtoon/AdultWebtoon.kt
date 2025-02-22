package eu.kanade.tachiyomi.extension.en.adultwebtoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Request

class AdultWebtoon : Madara("Adult Webtoon", "https://adultwebtoon.com", "en") {
    override val mangaSubString = "adult-webtoon"
    override val useNewChapterEndpoint = false
    override val sendViewCount = false
    override val fetchGenres = false
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaNextPageSelector() = "a.next"
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun oldXhrChaptersRequest(mangaId: String): Request {
        val form = FormBody.Builder()
            .add("action", "ajax_chap")
            .add("post_id", mangaId)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }
}
