package eu.kanade.tachiyomi.extension.en.manytoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Request

class ManyToon : Madara("ManyToon", "https://manytoon.com", "en") {

    override val mangaSubString = "comic"

    override val useNewChapterEndpoint = false
    override val sendViewCount = false
    override val fetchGenres = false

    override fun oldXhrChaptersRequest(mangaId: String): Request {
        val form = FormBody.Builder()
            .add("action", "ajax_chap")
            .add("post_id", mangaId)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }

    override fun searchMangaSelector() = "div.page-item-detail"
}
