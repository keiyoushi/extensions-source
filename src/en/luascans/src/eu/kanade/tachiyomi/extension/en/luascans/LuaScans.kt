package eu.kanade.tachiyomi.extension.en.luascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class LuaScans : MangaThemesia(
    "Lua Scans",
    "https://luascans.com",
    "en",
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
