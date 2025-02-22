package eu.kanade.tachiyomi.extension.tr.shadowceviri

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class ShadowCeviri : ZeistManga("Shadow Çeviri", "https://shadowceviri.blogspot.com", "tr") {

    // ============================== Popular ===============================
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.use { it.asJsoup() }
        val mangas = document.select("ul.gallery > li.bg").map { element ->
            SManga.create().apply {
                thumbnail_url = element.attr("style").substringAfter('(').substringBefore(')')
                title = element.selectFirst("h3")?.text() ?: "Manga"
                // NPE my beloved
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Chapters ==============================
    override val useOldChapterFeed = true
    override val chapterCategory = "Bölüm"
}
