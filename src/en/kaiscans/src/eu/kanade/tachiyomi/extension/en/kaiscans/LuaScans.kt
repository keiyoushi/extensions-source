package eu.kanade.tachiyomi.extension.en.kaiscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class LuaScans : MangaThemesia("Lua Scans (unoriginal)", "https://ponvi.online", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val id = 4825368993215448425
}
