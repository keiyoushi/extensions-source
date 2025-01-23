package eu.kanade.tachiyomi.extension.en.mangahentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Request

class MangaHentai : Madara("Manga Hentai", "https://mangahentai.me", "en") {
    override val mangaSubString = "manga-hentai"

    override val useNewChapterEndpoint: Boolean = false

    override fun oldXhrChaptersRequest(mangaId: String): Request {
        val form = FormBody.Builder()
            .add("action", "ajax_chap")
            .add("post_id", mangaId)
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }
}
