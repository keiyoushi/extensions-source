package eu.kanade.tachiyomi.extension.id.masterkomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class TenshiId : MangaThemesia(
    "Tenshi.id",
    "https://tenshi01.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id", "ID")),
    mangaUrlDirectory = "/komik",
) {

    // MasterKomik changed to Tenshi.id
    override val id: Long = 3146720114171452298

    override val seriesArtistSelector: String = ".imptdt-artist-sub-2 i .js-button-custom"
    override val seriesAuthorSelector: String = ".imptdt-author-sub-2 i .js-button-custom"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val projectPageString = "/project-list"

    override val hasProjectPage = true
}
