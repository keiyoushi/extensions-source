package eu.kanade.tachiyomi.extension.en.ydcomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class YDComics : MangaThemesia(
    "YD-Comics",
    "https://yd-comics.com",
    "en",
    mangaUrlDirectory = "/index.php/series",
) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return super.searchMangaRequest(page, query, filters).let {
            it.newBuilder()
                .url(it.url.newBuilder().encodedPath("$mangaUrlDirectory/").build())
                .build()
        }
    }
}
