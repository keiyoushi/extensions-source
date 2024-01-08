package eu.kanade.tachiyomi.extension.en.queenscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request

class QueenScans : MangaThemesia("Fairy Manga", "https://fairymanga.com", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val id = 4680104728999154642

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("/comics")) {
            manga.url.replaceFirst("/comics", mangaUrlDirectory)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("/comics")) {
            manga.url.replaceFirst("/comics", mangaUrlDirectory)
        }
        return super.chapterListRequest(manga)
    }
}
