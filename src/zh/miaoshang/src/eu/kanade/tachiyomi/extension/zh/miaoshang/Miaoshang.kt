package eu.kanade.tachiyomi.extension.zh.miaoshang

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup

class Miaoshang : MCCMS(
    "喵上漫画",
    "https://www.miaoshangmanhua.com",
    "zh",
    MiaoshangMCCMSConfig(),
) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    private class MiaoshangMCCMSConfig : MCCMSConfig(
        textSearchOnlyPageOne = true,
        lazyLoadImageAttr = "data-src",
    ) {
        override fun pageListParse(response: Response): List<Page> {
            val document = response.asJsoup()
            val container = document.select(".rd-article-wr")
            val comments = container.comments()

            return comments.filter { comment ->
                comment.data.contains(lazyLoadImageAttr)
            }.mapIndexed { i, comment ->
                Jsoup.parse(comment.data)
                    .selectFirst("img[$lazyLoadImageAttr]")?.attr(lazyLoadImageAttr).let { imageUrl ->
                        Page(i, imageUrl = imageUrl)
                    }
            }
        }
    }
}
