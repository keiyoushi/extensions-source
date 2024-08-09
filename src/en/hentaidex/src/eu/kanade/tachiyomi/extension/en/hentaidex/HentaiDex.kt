package eu.kanade.tachiyomi.extension.en.hentaidex

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiDex : MangaThemesia(
    "HentaiDex",
    "https://dexhentai.com",
    "en",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US),
) {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}
