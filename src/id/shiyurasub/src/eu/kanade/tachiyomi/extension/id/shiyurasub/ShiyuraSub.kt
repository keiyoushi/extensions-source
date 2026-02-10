package eu.kanade.tachiyomi.extension.id.shiyurasub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response

class ShiyuraSub : ZeistManga("ShiyuraSub", "https://shiyurasub.blogspot.com", "id") {

    override val hasFilters = true
    override val hasLanguageFilter = false

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        val manga = SManga.create().apply {
            val profileManga = document.selectFirst(mangaDetailsSelector)!!
            thumbnail_url = profileManga.selectFirst("img")!!.attr("abs:src")
            genre = profileManga.select(mangaDetailsSelectorGenres)
                .joinToString { it.text() }

            val infoElement = profileManga.select(mangaDetailsSelectorInfo)
            infoElement.forEach { element ->
                val infoText = element.ownText().trim().ifEmpty { element.selectFirst(mangaDetailsSelectorInfoTitle)?.text()?.trim() ?: "" }
                val descText = element.select(mangaDetailsSelectorInfoDescription).text().trim()
                when {
                    statusSelectorList.any { infoText.contains(it, ignoreCase = true) } -> {
                        status = parseStatus(descText)
                    }

                    authorSelectorList.any { infoText.contains(it, ignoreCase = true) } -> {
                        author = descText
                    }

                    artisSelectorList.any { infoText.contains(it, ignoreCase = true) } -> {
                        artist = descText
                    }
                }
            }
        }

        // PERBAIKAN: Gunakan selector yang benar untuk menargetkan paragraf deskripsi.
        manga.description = document.select("div.p-13 > p")
            .joinToString("\n\n") { it.text() }
            .trim()

        return manga
    }
}
