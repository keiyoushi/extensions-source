package eu.kanade.tachiyomi.extension.tr.pijamalikoi

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class PijamaliKoi : MangaThemesia(
    "Pijamalı Koi",
    "https://pijamalikoi.com/m",
    "tr",
    mangaUrlDirectory = "/seri",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override fun chapterListRequest(manga: SManga): Request {
        return super.chapterListRequest(manga).fixupUrl()
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return super.mangaDetailsRequest(manga).fixupUrl()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return super.pageListRequest(chapter).fixupUrl()
    }

    // Fixes the extra `/m` in the manga URL
    //
    // In the database URLs are stored as `/m/seri/regina-rena-to-the-unforgiven/`
    // With `*Request()` it becomes `$baseUrl/m/seri/regina-rena-to-the-unforgiven/`
    // Since `baseUrl` contains the the extra `/m`, it will double up, leading to an invalid URL.
    private fun Request.fixupUrl(): Request {
        return newBuilder().url(url.newBuilder().removePathSegment(0).build()).build()
    }
}
