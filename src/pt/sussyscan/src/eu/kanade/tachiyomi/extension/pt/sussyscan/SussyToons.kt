package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class SussyToons : GreenShit(
    "Sussy Toons",
    "https://www.sussytoons.wtf",
    "pt-BR",
) {
    override val id = 6963507464339951166

    override val versionId = 2

    override val contentOrigin: ContentOrigin = ContentOrigin.Mobile

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
