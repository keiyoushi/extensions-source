package eu.kanade.tachiyomi.extension.ja.raw1001

import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Raw1001 : Liliana() {

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val siteUrl = baseUrl.toHttpUrl()
        if (url.host != siteUrl.host && url.host !in MIRROR_HOSTS) return null

        return super.getMangaByUrl(url.newBuilder().host(siteUrl.host).build())
    }

    companion object {
        // Both domains serve the same pages, so a link from either opens on the chosen mirror
        private val MIRROR_HOSTS = setOf("raw1001.net", "rawkuro.net")
    }
}
