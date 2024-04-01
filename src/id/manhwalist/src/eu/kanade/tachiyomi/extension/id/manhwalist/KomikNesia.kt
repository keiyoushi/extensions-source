package eu.kanade.tachiyomi.extension.id.manhwalist

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class KomikNesia : MangaThemesia("KomikNesia", "https://komiknesia.xyz", "id") {

    // ManhwaList -> KomikNesia
    override val id = 4039555433611432280

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override val hasProjectPage = true

    override val pageSelector = "div#readerarea img.jetpack-lazy-image"
}
