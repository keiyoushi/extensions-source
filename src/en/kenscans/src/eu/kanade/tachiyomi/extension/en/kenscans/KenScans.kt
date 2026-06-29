package eu.kanade.tachiyomi.extension.en.kenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class KenScans :
    Iken(
        "Ken Scans",
        "en",
        "https://kencomics.com",
        "https://api.kencomics.com",
    ) {
    override fun chapterListRequest(manga: SManga): Request {
        // Migration from old web theme to the new one(Keyoapp -> Iken)
        if (manga.url.startsWith("/series/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val bodyString = response.body.string()
            .replace("\"createdAt\":", "\"_createdAt\":")
            .replace("\"updatedAt\":", "\"createdAt\":")

        val newResponse = response.newBuilder()
            .body(bodyString.toResponseBody(response.body.contentType()))
            .build()

        return super.chapterListParse(newResponse)
    }

    override fun pageListParse(response: Response): List<Page> {
        // Migration from old web theme to the new one(Keyoapp -> Iken)
        val url = response.request.url.toString().substringAfter(baseUrl)
        if (url.startsWith("/chapter/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }

        return super.pageListParse(response)
    }
}
