package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET

import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document

class Ikiru : MangaThemesia(
    "Ikiru",
    "Id",
    "https://id.ikiru.wtf/",
    "https://api.id.ikiru.wtf/",
) {
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
}
