package eu.kanade.tachiyomi.extension.en.suryascans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element

class GenzToons : Keyoapp(
    "Genz Toons",
    "https://genztoons.com",
    "en",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            if (element.select("img[src*=Coin.svg]").isNotEmpty()) {
                name = "🔒 $name"
            }
        }
    }
}
