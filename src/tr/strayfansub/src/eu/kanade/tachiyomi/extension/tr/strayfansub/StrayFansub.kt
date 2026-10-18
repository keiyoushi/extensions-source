package eu.kanade.tachiyomi.extension.tr.strayfansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source

@Source
abstract class StrayFansub : MangaThemesia() {
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pageList = super.getPageList(chapter)
        if (
            pageList.size == 1 &&
            pageList[0].imageUrl == "$baseUrl/wp-content/uploads/2026/04/WhatsApp-Image-2026-04-16-at-2.10.41-AM-2-266x150.jpeg"
        ) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return pageList
    }
}
