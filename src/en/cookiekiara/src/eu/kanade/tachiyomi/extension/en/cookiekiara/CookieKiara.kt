package eu.kanade.tachiyomi.extension.en.cookiekiara

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response

class CookieKiara : Madara("Cookie Kiara", "https://18.kiara.cool", "en") {
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }
}
