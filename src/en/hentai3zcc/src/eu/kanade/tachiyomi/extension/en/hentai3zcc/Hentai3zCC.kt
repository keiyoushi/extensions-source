package eu.kanade.tachiyomi.extension.en.hentai3zcc

import eu.kanade.tachiyomi.multisrc.manga18.Manga18
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class Hentai3zCC : Manga18("Hentai3z.CC", "https://hentai3z.cc", "en") {

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("div.mg_info > div.mg_name a")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
            ?.replace("cover_thumb_2.webp", "cover_250x350.jpg")
            ?.replace("admin.manga18.us", "bk.18porncomic.com")
    }
}
